// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// P09: çoklu eşzamanlı oturum. Her host bağlantısı kendi TerminalController'ı
// (kendi SSH kanalı) ile yaşar; UI aktif oturum çipiyle aralarında gezer.
data class SessionHandle(
    val id: String,
    val conn: SavedConnection,
    val controller: TerminalController,
)

data class HostKeyPrompt(val controller: TerminalController, val key: PresentedKey)

class SessionManager(
    private val scope: CoroutineScope,
    private val hostKeys: TofuHostKeyStore,
    private val connectorFactory: () -> SshConnector,
) {
    private val _sessions = MutableStateFlow<List<SessionHandle>>(emptyList())
    val sessions: StateFlow<List<SessionHandle>> = _sessions

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId

    private val _anyActive = MutableStateFlow(false)
    val anyActive: StateFlow<Boolean> = _anyActive

    // Aktif oturumun TOFU istemi (diyalog buradan beslenir).
    private val _hostKeyPrompt = MutableStateFlow<HostKeyPrompt?>(null)
    val hostKeyPrompt: StateFlow<HostKeyPrompt?> = _hostKeyPrompt

    // Kullanıcının verdiği oturum adları (session id → ad); boşsa conn.name.
    private val _customNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val customNames: StateFlow<Map<String, String>> = _customNames

    // Oturumun host'taki tmux adı (session id → "pa-xxxxxxxx"). Açılış
    // kaydına gömülür — uygulama yeniden açılınca aynı ada reattach edilir,
    // içinde çalışan agent/süreç hayatta kalır.
    private val _tmuxNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val tmuxNames: StateFlow<Map<String, String>> = _tmuxNames

    // Host'ta yaşayan pa-* oturumları (connId → liste) — diğer cihazların
    // açtığı ya da bu cihazda şu an açık olmayanlar. discoverRemote() doldurur.
    private val _remote = MutableStateFlow<Map<String, List<RemoteTerm>>>(emptyMap())
    val remote: StateFlow<Map<String, List<RemoteTerm>>> = _remote

    // Bu cihazın kimliği (ANDROID_ID) — registry'deki d= alanıyla karşılaştırılır.
    var deviceId: String = ""

    // Başka cihazca açılmış oturuma attach edilmiş session id'leri — close
    // bunlarda detach yapar (killRemote yok): sahibi değilsek öldürmeyiz.
    private val _sharedIds = MutableStateFlow<Set<String>>(emptySet())

    var onConnected: ((SavedConnection) -> Unit)? = null

    // Kopmada otomatik yeniden bağlanma tercihi (Ayarlar'dan beslenir).
    var autoReconnectOnDrop: Boolean = true

    fun active(): TerminalController? =
        _sessions.value.firstOrNull { it.id == _activeId.value }?.controller

    // Aynı profile ikinci açılış: mevcut oturuma geç; kapalıysa reconnect.
    // forceNew=true: aynı host'ta paralel oturum — dedupe atlanır.
    // startupCommand: bağlanınca gönderilecek komut (hazır profiller; dedupe
    // dalında çalışmaz — komutla açmak için forceNew kullan).
    // tmuxName: restore/attach akışından gelen kalıcı ad; nullsa benzersiz
    // üretilir. shared=true: oturum başka cihazca açıldı — close detach eder,
    // uzak tmux'u öldürmez.
    fun open(
        conn: SavedConnection,
        secret: Secret?,
        forceNew: Boolean = false,
        startupCommand: String? = null,
        customName: String? = null,
        tmuxName: String? = null,
        shared: Boolean = false,
    ): TerminalController {
        if (!forceNew) _sessions.value.firstOrNull { it.conn.id == conn.id }?.let { h ->
            _activeId.value = h.id
            if (h.controller.canReconnect()) h.controller.reconnect()
            return h.controller
        }
        val c = TerminalController(scope, connectorFactory(), hostKeys)
        c.autoReconnectOnDrop = autoReconnectOnDrop
        c.onConnected = { conn2 -> onConnected?.invoke(conn2) }
        val handle = SessionHandle(UUID.randomUUID().toString(), conn, c)
        scope.launch {
            c.pendingHostKey.collect { p ->
                _hostKeyPrompt.value = when {
                    p != null -> HostKeyPrompt(c, p)
                    _hostKeyPrompt.value?.controller == c -> null
                    else -> _hostKeyPrompt.value
                }
            }
        }
        scope.launch {
            c.state.collect { refreshAnyActive() }
        }
        _sessions.value = _sessions.value + handle
        customName?.trim()?.ifBlank { null }?.let { n ->
            _customNames.value = _customNames.value + (handle.id to n)
        }
        val tmux = tmuxName?.ifBlank { null }
            ?: "pa-" + UUID.randomUUID().toString().take(8)
        _tmuxNames.value = _tmuxNames.value + (handle.id to tmux)
        if (shared) _sharedIds.value = _sharedIds.value + handle.id
        _activeId.value = handle.id
        c.connect(conn, secret, startupCommand, tmux)
        // Registry'yi yalnızca BU cihazın yarattığı oturum yazar — reattach/
        // adopt mevcut kaydı ezmesin. Diğer cihazlar dosyadan ad+sahibi okur.
        if (tmuxName == null) {
            val disp = customName?.trim()?.ifBlank { null } ?: conn.name
            scope.launch {
                runCatching { c.state.first { it == ConnectionState.ACTIVE } }
                writeRegistry(c, tmux, disp)
            }
        }
        return c
    }

    // ~/.pocket-agent/terms/<tmux>: "n=<urlenc ad>\nd=<cihaz>" — değerler
    // URL-encoded/alnum olduğundan shell-quoting gerektirmez.
    private suspend fun writeRegistry(c: TerminalController, tmux: String, name: String?) {
        val e = c.exec() ?: return
        val enc = java.net.URLEncoder.encode(name ?: "", "UTF-8")
        runCatching {
            e.exec(
                "mkdir -p \"\$HOME/.pocket-agent/terms\" && " +
                    "printf 'n=%s\\nd=%s\\n' '$enc' '$deviceId' " +
                    "> \"\$HOME/.pocket-agent/terms/$tmux\"",
                5_000,
            )
        }
    }

    // Oturuma özel ad ver; null/boş → bağlantı adına döner.
    fun rename(id: String, name: String?) {
        val h = _sessions.value.firstOrNull { it.id == id } ?: return
        val n = name?.trim()?.ifBlank { null }
        _customNames.value = if (n == null) _customNames.value - id else _customNames.value + (id to n)
        // Registry'yi de tazele — diğer cihazlar yeni adı görsün.
        _tmuxNames.value[id]?.let { tmux ->
            scope.launch { writeRegistry(h.controller, tmux, n ?: h.conn.name) }
        }
    }

    // Host'taki canlı pa-* tmux oturumlarını keşfeder: aktif oturumu olan
    // her conn'in exec kanalından `tmux list-sessions` + registry taraması
    // (ölü registry dosyaları host'ta temizlenir). Yerelde açık olanlar
    // listede görünmez.
    fun discoverRemote() {
        _sessions.value.groupBy { it.conn.id }.forEach { (connId, hs) ->
            val ctl = hs.firstOrNull { it.controller.state.value == ConnectionState.ACTIVE }
                ?.controller ?: return@forEach
            val e = ctl.exec() ?: return@forEach
            scope.launch {
                val terms = try {
                    parseRemoteTerms(e.exec(REMOTE_TERMS_PROBE_CMD, 8_000).second)
                } catch (_: Exception) {
                    return@launch
                }
                val open = _tmuxNames.value.values.toSet()
                _remote.value = _remote.value +
                    (connId to terms.filter { it.tmux !in open })
            }
        }
    }

    // Kullanıcı kapattı: bu cihazın açtığı oturumda uzak tmux öldürülür;
    // paylaşılan (başka cihazın) oturumda yalnızca detach edilir.
    fun close(id: String) {
        val h = _sessions.value.firstOrNull { it.id == id } ?: return
        h.controller.disconnect(killRemote = id !in _sharedIds.value)
        _sessions.value = _sessions.value - h
        _customNames.value = _customNames.value - id
        _tmuxNames.value = _tmuxNames.value - id
        _sharedIds.value = _sharedIds.value - id
        if (_activeId.value == id) _activeId.value = _sessions.value.lastOrNull()?.id
        refreshAnyActive()
    }

    fun setActive(id: String) {
        if (_sessions.value.any { it.id == id }) _activeId.value = id
    }

    fun closeAll() {
        val shared = _sharedIds.value
        _sessions.value.forEach {
            it.controller.disconnect(killRemote = it.id !in shared)
        }
        _sessions.value = emptyList()
        _customNames.value = emptyMap()
        _tmuxNames.value = emptyMap()
        _sharedIds.value = emptySet()
        _activeId.value = null
        _anyActive.value = false
    }

    private fun refreshAnyActive() {
        _anyActive.value = _sessions.value.any { it.controller.state.value == ConnectionState.ACTIVE }
    }
}
