// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.room.Room
import dev.pocketagent.data.AgentEventEntity
import dev.pocketagent.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DaoSanityTest {
    @Test fun insertAndRead() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val now = System.currentTimeMillis()
        db.events().insert(AgentEventEntity("e1", "s1", "codex", "APPROVAL", "t", now, now + 1000))
        assertEquals(1, db.events().active(now).size)
    }
}
