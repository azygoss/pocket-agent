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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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

    @Test fun forceNewOpensParallelSessionOnSameHost() = runBlocking {
        val m = manager()
        m.open(c1, Secret.Password("p"))
        // forceNew: dedupe atlanır → aynı conn.id'li ikinci oturum.
        val t2 = m.open(c1, Secret.Password("p"), forceNew = true)
        assertEquals(2, m.sessions.value.size)
        // Yeni oturum aktif olur ve controller'ları farklıdır.
        assertSame(t2, m.active())
        assertEquals(2, m.sessions.value.map { it.controller }.toSet().size)
        // Üçüncüsü de açılır; normal open yine ilkine odaklanır.
        m.open(c1, Secret.Password("p"), forceNew = true)
        assertEquals(3, m.sessions.value.size)
        val first = m.sessions.value.first { it.conn.id == c1.id }
        assertSame(first.controller, m.open(c1, Secret.Password("p")))
        m.closeAll()
    }

    @Test fun renameSetsAndClearsCustomName() = runBlocking {
        val m = manager()
        m.open(c1, Secret.Password("p"))
        val id = m.sessions.value.single().id
        assertNull(m.customNames.value[id])

        m.rename(id, "prod-web")
        assertEquals("prod-web", m.customNames.value[id])

        // Boş ad → bağlantı adına dönüş (kayıt silinir).
        m.rename(id, "   ")
        assertNull(m.customNames.value[id])

        // Oturum kapanınca ad da temizlenir.
        m.rename(id, "tekrar")
        m.close(id)
        assertTrue(m.customNames.value.isEmpty())
    }

    @Test fun openWithCustomNameRegistersIt() = runBlocking {
        val m = manager()
        m.open(c1, Secret.Password("p"), customName = "restore-adı")
        val id = m.sessions.value.single().id
        assertEquals("restore-adı", m.customNames.value[id])
        m.closeAll()
    }

    // Restore akışı: kalıcı tmux adı kayıtlı gelir ve kapanana dek durur.
    @Test fun tmuxNameRegisteredUntilClose() = runBlocking {
        val m = manager()
        m.open(c1, Secret.Password("p"), tmuxName = "pa-restore1")
        val id = m.sessions.value.single().id
        assertEquals("pa-restore1", m.tmuxNames.value[id])
        m.close(id)
        assertNull(m.tmuxNames.value[id])
    }

    // Ad verilmezse her oturuma benzersiz pa-<id> üretilir — paralel
    // oturumlar aynı uzak tmux'a çakışmaz.
    @Test fun openGeneratesUniqueTmuxNames() = runBlocking {
        val m = manager()
        m.open(c1, Secret.Password("p"))
        m.open(c1, Secret.Password("p"), forceNew = true)
        val names = m.sessions.value.mapNotNull { m.tmuxNames.value[it.id] }
        assertEquals(2, names.size)
        assertEquals(2, names.toSet().size)
        assertTrue(names.all { it.startsWith("pa-") })
        m.closeAll()
    }
}

// open_sessions kaydı: "connId|urlEncodedAd|tmuxAd" — virgüllü/özel
// karakterli adlar ve eksik alanlı (eski format) kayıtlar doğru çözülmeli.
@RunWith(RobolectricTestRunner::class)
class OpenSessionsStoreTest {
    @Test fun openSessionsRoundtripWithTmux() = runBlocking {
        val store = dev.pocketagent.data.DataStoreSettingsStore(RuntimeEnvironment.getApplication())
        store.saveOpenSessions(listOf(
            dev.pocketagent.data.OpenSession("c1", "web, özel ·1", "pa-abc12345"),
            dev.pocketagent.data.OpenSession("c2", null, null),
        ))
        assertEquals(
            listOf(
                dev.pocketagent.data.OpenSession("c1", "web, özel ·1", "pa-abc12345"),
                dev.pocketagent.data.OpenSession("c2", null, null),
            ),
            store.loadOpenSessions(),
        )
    }
}
