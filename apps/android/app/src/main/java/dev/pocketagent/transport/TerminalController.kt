// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// P06/P08: session lifecycle. Auth and host-key failures are terminal
// (hard-stop, no fallback — P08 rule); network failures are reported as-is
// until Mosh/ET transports land (caps currently SSH-only).
class TerminalController(
    private val scope: CoroutineScope,
    private val connector: SshConnector,
    private val hostKeys: TofuHostKeyStore,
) {
    val vm = TerminalViewModel(SessionId("session:local"))

    private val _state = MutableStateFlow(ConnectionState.CLOSED)
    val state: StateFlow<ConnectionState> = _state

    private val _failure = MutableStateFlow<TransportFailure?>(null)
    val failure: StateFlow<TransportFailure?> = _failure

    private val _pendingHostKey = MutableStateFlow<PresentedKey?>(null)
    val pendingHostKey: StateFlow<PresentedKey?> = _pendingHostKey

    private val _connectedTo = MutableStateFlow<SavedConnection?>(null)
    val connectedTo: StateFlow<SavedConnection?> = _connectedTo

    private var job: Job? = null
    private var retryJob: Job? = null
    private var transport: SshTransport? = null
    private var lastConn: SavedConnection? = null
    private var lastSecret: Secret? = null
    private var lastStartupCommand: String? = null

    // Kopmada otomatik yeniden bağlanma (SessionManager ayarından beslenir).
    // Auth/host-key hataları hard-stop kalır (P08) — yalnız ağ kopması/EOF retried edilir.
    var autoReconnectOnDrop: Boolean = true
    var retryBaseMs: Long = 2_000 // testlerde kısaltılır
    private var manualClose: Boolean = true
    private var retryCount = 0

    // 0 = yeniden deneme yok; >0 = n. deneme sürüyor (UI rozeti)
    private val _retryAttempt = MutableStateFlow(0)
    val retryAttempt: StateFlow<Int> = _retryAttempt

    companion object { const val MAX_RETRY = 5 }

    @Synchronized
    fun connect(conn: SavedConnection, secret: Secret?, startupCommand: String? = null) {
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.ACTIVE) return
        retryJob?.cancel()
        _retryAttempt.value = 0
        lastConn = conn
        lastSecret = secret
        lastStartupCommand = startupCommand
        doConnect(conn, secret, startupCommand)
    }

    // Son bağlantıyı (varsa) yeniden kurar; secret RAM'de tutulanla aynı.
    fun reconnect(): Boolean {
        val c = lastConn ?: return false
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.ACTIVE) return false
        disconnect()
        doConnect(c, lastSecret, lastStartupCommand)
        return true
    }

    fun canReconnect(): Boolean =
        lastConn != null && (_state.value == ConnectionState.CLOSED || _state.value == ConnectionState.FAILED)

    private fun doConnect(conn: SavedConnection, secret: Secret?, startupCommand: String? = null) {
        manualClose = false
        _failure.value = null
        _pendingHostKey.value = null
        vm.clear()
        vm.setBadge(TerminalTransport.SSH)
        _state.value = ConnectionState.CONNECTING
        job = scope.launch {
            try {
                // PTY bu boyutla açılıyor — dedupe tabanı burada kurulur.
                lastPtySize = vm.size
                val t = connector.open(conn, secret, vm.size)
                transport = t
                _connectedTo.value = conn
                _state.value = ConnectionState.ACTIVE
                _retryAttempt.value = 0
                retryCount = 0
                onConnected?.invoke(conn)
                // Açılış komutları: kabuk hazır olsun diye kısa gecikme.
                // `clear` en başta: MOTD/banner/son-giriş bilgisi silinir,
                // prompt üstte temiz açılır. tmux attach'ten ÖNCE çalışır —
                // var olan pane içeriği silinmez (attach alt-screen'e geçer).
                val startupCmds = buildList {
                    add("clear")
                    if (conn.autoTmux) add("tmux new-session -A -s main")
                    startupCommand?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                }
                scope.launch {
                    startupCmds.forEach { cmd ->
                        kotlinx.coroutines.delay(600)
                        runCatching { t.send(TerminalInput.Text("$cmd\n")) }
                    }
                }
                readLoop(t)
            } catch (e: UnknownHostKeyException) {
                _pendingHostKey.value = e.presented
                _state.value = ConnectionState.CLOSED
            } catch (e: HostKeyChangedException) {
                _failure.value = TransportFailure.HostKeyChanged
                _state.value = ConnectionState.FAILED
            } catch (e: AuthFailedException) {
                _failure.value = TransportFailure.AuthFailed
                _state.value = ConnectionState.FAILED
            } catch (e: TransportFailureException) {
                _failure.value = e.failure
                _state.value = ConnectionState.FAILED
            } catch (e: Exception) {
                _failure.value = TransportFailure.Network(e.message ?: e.javaClass.simpleName)
                _state.value = ConnectionState.FAILED
            }
        }
    }
    // Başarılı bağlantıda tetiklenir (örn. lastConnectedAt güncellemesi).
    var onConnected: ((SavedConnection) -> Unit)? = null

    // Aktif transport SFTP destekliyorsa döner (dosya sekmesi buradan beslenir).
    fun sftp(): SftpSession? = transport as? SftpSession

    // Aktif transport gateway tüneli açabiliyorsa döner (P11 workspace erişimi).
    fun gateway(): GatewayTunnel? = transport as? GatewayTunnel

    // User confirmed the fingerprint: pin it, then retry the same connection.
    fun acceptHostKeyAndReconnect() {
        val p = _pendingHostKey.value ?: return
        hostKeys.pin(p)
        _pendingHostKey.value = null
        val c = lastConn ?: return
        val s = lastSecret
        _state.value = ConnectionState.CLOSED
        connect(c, s, lastStartupCommand)
    }

    fun rejectHostKey() {
        _pendingHostKey.value = null
    }

    // Uzak PTY'nin bildiği son boyut: aynı boyuta tekrar Resize göndermek
    // uzak shell'i SIGWINCH ile uyandırır — zsh/fish prompt'u yeniden basar,
    // ekranda fazladan prompt satırları birikir.
    @Volatile private var lastPtySize: TerminalSize? = null

    fun send(input: TerminalInput) {
        val t = transport ?: return
        if (input is TerminalInput.Resize) {
            val prev = lastPtySize
            if (input.size == prev) return
            // Satır-only değişim + düz shell (alt-screen yok) → SIGWINCH
            // gönderme: satır sayısı satır-bazlı çıktıyı etkilemez ama zsh/fish
            // her WINCH'te prompt'u yeniden basar. Klavye aç/kapa tam olarak
            // bu durum — ekranda boşluklu prompt tekrarları birikiyordu.
            // Alt-screen aktifse (vim/htop/less/tmux) resize şart.
            if (prev != null && input.size.cols == prev.cols && !vm.altScreen.value) return
            lastPtySize = input.size
        }
        scope.launch {
            try {
                t.send(input)
            } catch (_: Exception) {
                // dead transport; read loop reports the state change
            }
        }
    }

    @Synchronized
    fun disconnect() {
        manualClose = true
        retryJob?.cancel()
        retryJob = null
        _retryAttempt.value = 0
        retryCount = 0
        job?.cancel()
        runCatching { transport?.close() }
        transport = null
        _connectedTo.value = null
        _state.value = ConnectionState.CLOSED
    }

    // Beklenmeyen kopma (EOF/ağ) sonrası üstel geri çekilmeyle yeniden dener.
    // Hard-stop: auth/host-key hatası, kullanıcı kapatması, maks deneme.
    private fun scheduleRetry() {
        if (!autoReconnectOnDrop || manualClose) return
        val c = lastConn ?: return
        val s = lastSecret
        retryJob?.cancel()
        retryJob = scope.launch {
            var delayMs = retryBaseMs
            while (retryCount < MAX_RETRY) {
                retryCount++
                _retryAttempt.value = retryCount
                kotlinx.coroutines.delay(delayMs)
                if (manualClose || _state.value != ConnectionState.CLOSED) break
                doConnect(c, s, lastStartupCommand)
                // CONNECTING çözümlenene kadar bekle (maks 15s)
                var waited = 0L
                while (_state.value == ConnectionState.CONNECTING && waited < 15_000) {
                    kotlinx.coroutines.delay(100); waited += 100
                }
                if (_state.value == ConnectionState.ACTIVE) break
                // Auth/host-key hatası → P08 hard-stop, denemeyi bırak
                val f = _failure.value
                if (f is TransportFailure.AuthFailed || f is TransportFailure.HostKeyChanged) break
                delayMs = (delayMs * 2).coerceAtMost(32_000)
            }
            _retryAttempt.value = 0
        }
    }

    private suspend fun readLoop(t: SshTransport) {
        try {
            while (currentCoroutineContext().isActive) {
                val first = t.read()
                // Burst toplama: hazır bekleyen frame'leri tek güncellemeye
                // kat — UI her 1KB parçada değil, batch başına recombine olur.
                var bytes = first.bytes
                var drained = 0
                while (drained < 64 && bytes.size < 256 * 1024) {
                    val nxt = t.poll() ?: break
                    bytes += nxt.bytes
                    drained++
                }
                vm.onFrame(TerminalFrame(bytes, first.transport))
            }
        } catch (_: Exception) {
            // channel closed / EOF / remote hangup
        } finally {
            if (_state.value == ConnectionState.ACTIVE) {
                _state.value = ConnectionState.CLOSED
                _connectedTo.value = null
            }
            // Uzaktan kopma (kullanıcı kapatmadıysa) → otomatik yeniden dene
            if (_state.value == ConnectionState.CLOSED) scheduleRetry()
        }
    }
}
