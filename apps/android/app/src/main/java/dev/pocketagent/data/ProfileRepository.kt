// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.data

import dev.pocketagent.transport.SavedProfile
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Hazır terminal profilleri: ad + komut (+ isteğe bağlı host bağlantısı).
// Secret tutmaz — yalnız komut metni ve bağlantı referansı.
class ProfileRepository(
    private val dao: ProfileDao,
) {
    private val _items = MutableStateFlow<List<SavedProfile>>(emptyList())
    val items: StateFlow<List<SavedProfile>> = _items

    suspend fun refresh() {
        _items.value = dao.all().map { it.toModel() }
    }

    suspend fun upsert(p: SavedProfile): String {
        require(p.name.isNotBlank() && p.command.isNotBlank()) { "invalid profile" }
        val id = p.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(p.copy(id = id).toEntity())
        refresh()
        return id
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        refresh()
    }
}

fun ProfileEntity.toModel() = SavedProfile(
    name = name,
    command = command,
    connectionId = connectionId,
    id = id,
)

fun SavedProfile.toEntity() = ProfileEntity(
    id = id,
    name = name,
    command = command,
    connectionId = connectionId,
)
