// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.shortHash
import dev.pocketagent.ui.InboxRow
import dev.pocketagent.ui.activeSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveAgentsTest {

    private fun row(sess: String, cat: String, t: String) = InboxRow(
        sessionId = sess, eventId = "event:$sess-$cat-$t", title = "m",
        source = "claude", category = cat, createdAt = t, host = "h:x",
    )

    @Test
    fun startedWithoutEndIsActive() {
        val rows = listOf(row("proc:1", "SESSION_STARTED", "2026-09-10T19:00:00Z"))
        assertEquals(1, activeSessions(rows).size)
    }

    @Test
    fun endedSessionIsNotActive() {
        val rows = listOf(
            row("tmux:work", "SESSION_STARTED", "2026-09-10T19:00:00Z"),
            row("tmux:work", "SESSION_ENDED", "2026-09-10T19:01:00Z"),
        )
        assertTrue(activeSessions(rows).isEmpty())
    }

    @Test
    fun restartAfterEndIsActive() {
        val rows = listOf(
            row("tmux:work", "SESSION_STARTED", "2026-09-10T19:00:00Z"),
            row("tmux:work", "SESSION_ENDED", "2026-09-10T19:01:00Z"),
            row("tmux:work", "SESSION_STARTED", "2026-09-10T19:02:00Z"),
        )
        assertEquals(1, activeSessions(rows).size)
    }

    @Test
    fun shortHashMatchesGo() {
        // Go emit.shortHash("host:devbox") — her part + \x00, ilk 16 hex.
        // Referans değer: python3 sha256("host:devbox\x00").hexdigest()[:16]
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("host:devbox".toByteArray() + byteArrayOf(0))
            .joinToString("") { "%02x".format(it) }.take(16)
        assertEquals(expected, shortHash("host:devbox"))
    }
}
