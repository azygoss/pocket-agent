// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.FakeSshTransport
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

private class OkConnector : SshConnector {
    override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
        FakeSshTransport().also { it.openPty("xterm-256color", size) }
}

class SessionManagerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val c1 = SavedConnection("a", "h1", 22, "u", "ram:password", id = "c1")
    private val c2 = SavedConnection("b", "h2", 22, "u", "ram:password", id = "c2")

    private fun manager(): SessionManager {
        val kh = File.createTempFile("khst", null).apply { delete() }
        return SessionManager(scope, TofuHostKeyStore(kh)) { OkConnector() }
    }

    @Test fun openSwitchClose() = runBlocking {
        val m = manager()
        val t1 = m.open(c1, Secret.Password("p"))
        assertEquals(1, m.sessions.value.size)
        // aynı profil ikinci açılış: yeni oturum değil, mevcut
        assertSame(t1, m.open(c1, Secret.Password("p")))
        assertEquals(1, m.sessions.value.size)

        m.open(c2, Secret.Password("p"))
        assertEquals(2, m.sessions.value.size)
        assertEquals(c2.id, m.sessions.value.first { it.id == m.activeId.value }.conn.id)

        m.setActive(m.sessions.value.first { it.conn.id == c1.id }.id)
        assertEquals(c1.id, m.sessions.value.first { it.id == m.activeId.value }.conn.id)

        m.close(m.sessions.value.first { it.conn.id == c1.id }.id)
        assertEquals(1, m.sessions.value.size)
        m.closeAll()
        assertEquals(0, m.sessions.value.size)
        assertNull(m.activeId.value)
        assertNull(m.active())
    }
}
