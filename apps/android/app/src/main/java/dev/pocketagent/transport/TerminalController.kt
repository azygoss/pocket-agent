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
    private var transport: SshTransport? = null
    private var lastConn: SavedConnection? = null
    private var lastSecret: Secret? = null

    @Synchronized
    fun connect(conn: SavedConnection, secret: Secret?) {
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.ACTIVE) return
        lastConn = conn
        lastSecret = secret
        doConnect(conn, secret)
    }

    // Son bağlantıyı (varsa) yeniden kurar; secret RAM'de tutulanla aynı.
    fun reconnect(): Boolean {
        val c = lastConn ?: return false
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.ACTIVE) return false
        disconnect()
        doConnect(c, lastSecret)
        return true
    }

    fun canReconnect(): Boolean =
        lastConn != null && (_state.value == ConnectionState.CLOSED || _state.value == ConnectionState.FAILED)

    private fun doConnect(conn: SavedConnection, secret: Secret?) {
        _failure.value = null
        _pendingHostKey.value = null
        vm.clear()
        vm.setBadge(TerminalTransport.SSH)
        _state.value = ConnectionState.CONNECTING
        job = scope.launch {
            try {
                val t = connector.open(conn, secret, vm.size)
                transport = t
                _connectedTo.value = conn
                _state.value = ConnectionState.ACTIVE
                onConnected?.invoke(conn)
                // tmux otomatik bağlanma: kabuk hazır olsun diye kısa gecikme.
                if (conn.autoTmux) {
                    scope.launch {
                        kotlinx.coroutines.delay(600)
                        runCatching { t.send(TerminalInput.Text("tmux new-session -A -s main\n")) }
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
        connect(c, s)
    }

    fun rejectHostKey() {
        _pendingHostKey.value = null
    }

    fun send(input: TerminalInput) {
        val t = transport ?: return
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
        job?.cancel()
        runCatching { transport?.close() }
        transport = null
        _connectedTo.value = null
        _state.value = ConnectionState.CLOSED
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
        }
    }
}
