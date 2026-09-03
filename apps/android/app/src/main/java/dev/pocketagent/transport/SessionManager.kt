// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    var onConnected: ((SavedConnection) -> Unit)? = null

    fun active(): TerminalController? =
        _sessions.value.firstOrNull { it.id == _activeId.value }?.controller

    // Aynı profile ikinci açılış: mevcut oturuma geç; kapalıysa reconnect.
    fun open(conn: SavedConnection, secret: Secret?): TerminalController {
        _sessions.value.firstOrNull { it.conn.id == conn.id }?.let { h ->
            _activeId.value = h.id
            if (h.controller.canReconnect()) h.controller.reconnect()
            return h.controller
        }
        val c = TerminalController(scope, connectorFactory(), hostKeys)
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
        _activeId.value = handle.id
        c.connect(conn, secret)
        return c
    }

    fun close(id: String) {
        val h = _sessions.value.firstOrNull { it.id == id } ?: return
        h.controller.disconnect()
        _sessions.value = _sessions.value - h
        if (_activeId.value == id) _activeId.value = _sessions.value.lastOrNull()?.id
        refreshAnyActive()
    }

    fun setActive(id: String) {
        if (_sessions.value.any { it.id == id }) _activeId.value = id
    }

    fun closeAll() {
        _sessions.value.forEach { it.controller.disconnect() }
        _sessions.value = emptyList()
        _activeId.value = null
        _anyActive.value = false
    }

    private fun refreshAnyActive() {
        _anyActive.value = _sessions.value.any { it.controller.state.value == ConnectionState.ACTIVE }
    }
}
