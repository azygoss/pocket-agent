// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.net.BackendEvent
import dev.pocketagent.ui.InboxViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Inbox kalıcılığı (0.9.2): olaylar Room'a yazılır, yeniden başlatmada geri
// yüklenir; resolve/markRead DB'ye yansır. Testler persistVersion flow'unu
// bekler — DB'yi döngüde yoklamak in-memory tek-bağlantı yazıcıyı aç bırakır.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InboxPersistenceTest {
    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), AppDatabase::class.java,
    ).allowMainThreadQueries().build()

    private fun ev(id: String, msg: String) = BackendEvent(
        eventId = "event:$id", host = "host:1", session = "sess:1", source = "codex",
        category = "APPROVAL_REQUIRED", message = msg, digest = "d$id", revision = "1",
        createdAt = java.time.Instant.now().toString(), expiresAt = "",
    )

    @Test fun eventsSurviveRestart() = runBlocking {
        val db = db()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm1 = InboxViewModel(db.events(), scope)
        assertEquals(2, vm1.mergeRemote(listOf(ev("a", "Deploy?"), ev("b", "Restart?"))))
        withTimeout(10_000) { vm1.persistVersion.first { it >= 2 } }
        assertNull(vm1.persistError)

        // "Yeniden başlatma": yeni VM aynı DB'den yükler
        val vm2 = InboxViewModel(db.events(), scope)
        withTimeout(10_000) { while (vm2.rows.size < 2) kotlinx.coroutines.delay(20) }
        assertEquals(2, vm2.rows.size)
        assertTrue(vm2.rows.any { it.title == "Deploy?" && it.digest == "da" })
    }

    @Test fun resolveRemovesFromDb() = runBlocking {
        val db = db()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = InboxViewModel(db.events(), scope)
        vm.mergeRemote(listOf(ev("a", "Deploy?")))
        withTimeout(10_000) { vm.persistVersion.first { it >= 1 } }
        vm.resolve("event:a")
        withTimeout(10_000) { vm.persistVersion.first { it >= 2 } }
        assertNull(vm.persistError)
        assertEquals(0, vm.rows.size)
        assertTrue(db.events().active(System.currentTimeMillis()).isEmpty())
    }

    @Test fun markReadPersists() = runBlocking {
        val db = db()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = InboxViewModel(db.events(), scope)
        vm.mergeRemote(listOf(ev("a", "Deploy?")))
        withTimeout(10_000) { vm.persistVersion.first { it >= 1 } }
        vm.markRead("event:a")
        withTimeout(10_000) { vm.persistVersion.first { it >= 2 } }
        assertNull(vm.persistError)
        // yeniden yükleme: okundu bayrağı korunur
        assertFalse(db.events().active(System.currentTimeMillis()).first().unread)
        val vm2 = InboxViewModel(db.events(), scope)
        withTimeout(10_000) { while (vm2.rows.isEmpty()) kotlinx.coroutines.delay(20) }
        assertFalse(vm2.rows.first().unread)
    }
}
