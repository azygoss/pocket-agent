// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.data

import dev.pocketagent.security.SecretStore
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.TerminalTransport
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// P05: Room-backed connection profiles. Secret'lar ya RAM-only haritada ya
// da (kullanıcı "hatırla" derse) KeystoreSecretStore'da şifreli — asla
// plaintext DB'de değil (plan §2.3).
class ConnectionRepository(
    private val dao: ConnectionDao,
    private val secretStore: SecretStore? = null,
) {
    private val _items = MutableStateFlow<List<SavedConnection>>(emptyList())
    val items: StateFlow<List<SavedConnection>> = _items

    private val ramSecrets = HashMap<String, Secret>()

    suspend fun refresh() {
        _items.value = dao.all().map { it.toModel() }
    }

    // remember=true → Keystore şifreli kalıcı; false → yalnız RAM.
    suspend fun upsert(c: SavedConnection, secret: Secret?, remember: Boolean = false): String {
        require(c.validate().isEmpty()) { "invalid connection: ${c.validate()}" }
        val id = c.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(c.copy(id = id).toEntity())
        if (secret != null) {
            synchronized(ramSecrets) { ramSecrets.remove(id) }
            val persisted = remember && secretStore?.save(id, secret) == true
            if (!persisted) synchronized(ramSecrets) { ramSecrets[id] = secret }
            if (!remember) secretStore?.delete(id) // tercih değiştiyse kalıcı kopyayı temizle
        }
        // secret == null → önceki secret (RAM veya Keystore) korunur
        refresh()
        return id
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        synchronized(ramSecrets) { ramSecrets.remove(id) }
        secretStore?.delete(id)
        refresh()
    }

    suspend fun touch(id: String, at: Long = System.currentTimeMillis()) {
        dao.touch(id, at)
        refresh()
    }

    fun secret(id: String): Secret? =
        synchronized(ramSecrets) { ramSecrets[id] } ?: secretStore?.load(id)

    fun hasSavedSecret(id: String): Boolean =
        synchronized(ramSecrets) { ramSecrets.containsKey(id) } || secretStore?.has(id) == true

    fun wipeSecrets() {
        synchronized(ramSecrets) { ramSecrets.clear() }
    }
}

fun ConnectionEntity.toModel() = SavedConnection(
    name = name,
    host = host,
    port = port,
    user = user,
    credentialRef = credentialRef,
    transportOrder = transportOrder.split(",").mapNotNull {
        runCatching { TerminalTransport.valueOf(it.trim()) }.getOrNull()
    }.ifEmpty { listOf(TerminalTransport.SSH) },
    jumpHost = jumpHost,
    etPort = etPort,
    agentForward = agentForward,
    autoTmux = autoTmux,
    id = id,
    lastConnectedAt = lastConnectedAt,
)

fun SavedConnection.toEntity() = ConnectionEntity(
    id = id,
    name = name,
    host = host,
    port = port,
    user = user,
    credentialRef = credentialRef,
    transportOrder = transportOrder.joinToString(",") { it.name },
    jumpHost = jumpHost,
    etPort = etPort,
    agentForward = agentForward,
    autoTmux = autoTmux,
    lastConnectedAt = lastConnectedAt,
)
