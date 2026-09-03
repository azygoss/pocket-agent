// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectionRepositoryTest {
    private fun repo(): ConnectionRepository {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        return ConnectionRepository(db.connections()) // secretStore yok → RAM-only
    }

    @Test fun upsertEditDeleteAndTouch() = runBlocking {
        val r = repo()
        val id = r.upsert(
            SavedConnection("h1", "example.com", 22, "u", "ram:password"),
            Secret.Password("pw"),
        )
        assertEquals(1, r.items.value.size)
        assertEquals(Secret.Password("pw"), r.secret(id))

        // Düzenleme: secret null → eski secret korunur
        r.upsert(SavedConnection("h1b", "example.com", 2222, "u", "ram:password", id = id), null)
        assertEquals("h1b", r.items.value[0].name)
        assertEquals(2222, r.items.value[0].port)
        assertEquals(Secret.Password("pw"), r.secret(id))

        // touch: lastConnectedAt güncellenir ve sıralamada öne gelir
        r.upsert(SavedConnection("h2", "other.com", 22, "u", "ram:password"), Secret.Password("x"))
        r.touch(id, 999999)
        assertEquals(id, r.items.value[0].id)
        assertEquals(999999, r.items.value[0].lastConnectedAt)

        r.delete(id)
        assertEquals(1, r.items.value.size)
        assertNull(r.secret(id))
        assertFalse(r.hasSavedSecret(id))
    }
}
