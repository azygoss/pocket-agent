// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.data

import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.TerminalTransport
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// P05: Room-backed connection profiles. Secrets (password/PEM) are held in a
// RAM-only map keyed by profile id — never in the DB (plan §2.3).
class ConnectionRepository(private val dao: ConnectionDao) {
    private val _items = MutableStateFlow<List<SavedConnection>>(emptyList())
    val items: StateFlow<List<SavedConnection>> = _items

    private val secrets = HashMap<String, Secret>()

    suspend fun refresh() {
        _items.value = dao.all().map { it.toModel() }
    }

    suspend fun upsert(c: SavedConnection, secret: Secret?): String {
        require(c.validate().isEmpty()) { "invalid connection: ${c.validate()}" }
        val id = c.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(c.copy(id = id).toEntity())
        if (secret != null) secrets[id] = secret
        refresh()
        return id
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        synchronized(secrets) { secrets.remove(id) }
        refresh()
    }

    fun secret(id: String): Secret? = synchronized(secrets) { secrets[id] }

    fun wipeSecrets() = synchronized(secrets) { secrets.clear() }
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
    id = id,
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
)
