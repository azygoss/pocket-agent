// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.data.ProfileRepository
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.SavedProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileRepositoryTest {
    private fun db(): AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
    ).allowMainThreadQueries().build()

    @Test fun upsertEditDelete() = runBlocking {
        val pr = ProfileRepository(db().profiles())
        val id = pr.upsert(SavedProfile("Codex", "codex"))
        assertEquals(1, pr.items.value.size)
        assertEquals("codex", pr.items.value[0].command)
        assertNull(pr.items.value[0].connectionId)

        // Düzenleme: aynı id üstüne yazar.
        pr.upsert(SavedProfile("Codex dev", "codex resume", id = id))
        assertEquals(1, pr.items.value.size)
        assertEquals("Codex dev", pr.items.value[0].name)

        pr.delete(id)
        assertTrue(pr.items.value.isEmpty())
    }

    @Test fun blankFieldsRejected() {
        val pr = ProfileRepository(db().profiles())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { pr.upsert(SavedProfile("", "codex")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { pr.upsert(SavedProfile("Codex", " ")) }
        }
    }

    // Host silinirse FK SET_NULL: profil kalır, açılışta yeniden host sorar.
    @Test fun connectionDeleteDetachesProfile() = runBlocking {
        val db = db()
        val cr = ConnectionRepository(db.connections())
        val pr = ProfileRepository(db.profiles())
        val cid = cr.upsert(SavedConnection("vps", "x.com", 22, "u", "ram:password"), null)
        val pid = pr.upsert(SavedProfile("Codex", "codex", connectionId = cid))
        assertEquals(cid, pr.items.value[0].connectionId)

        cr.delete(cid)
        pr.refresh()
        assertEquals(1, pr.items.value.size)
        assertNull(pr.items.value.first { it.id == pid }.connectionId)
    }
}
