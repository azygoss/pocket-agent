// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.RemoteTerm
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.transport.parseRemoteTerms
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Cihazlar-arası keşif: tmux listesi + registry parse'ı; sahiplik modeli
// (başkasının oturumunda close = detach, kendi oturumunda = kill + rm).
@RunWith(RobolectricTestRunner::class)
class RemoteTermsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val conn = SavedConnection("t", "h", 22, "u", "ram:password", id = "c1")

    private class ExecConnector(val t: ExecRecordingTransport) : SshConnector {
        override suspend fun open(c: SavedConnection, s: Secret?, size: TerminalSize): SshTransport {
            t.inner.openPty("xterm-256color", size)
            return t
        }
    }

    private fun manager(t: ExecRecordingTransport): SessionManager {
        val kh = File.createTempFile("khst", null).apply { delete() }
        return SessionManager(scope, TofuHostKeyStore(kh)) { ExecConnector(t) }
            .apply { deviceId = "dev-A" }
    }

    private suspend fun awaitActive(m: SessionManager) = withTimeout(5_000) {
        while (m.sessions.value.none {
            it.controller.state.value == dev.pocketagent.transport.ConnectionState.ACTIVE
        }) delay(10)
    }

    // ── parseRemoteTerms ────────────────────────────────────────────────

    @Test fun parseMergesLiveSessionsAndRegistry() {
        val out = "pa-a1b2c3d4\npa-e5f6a7b8\nmain\n@@REG@@\n" +
            "@@F@@pa-a1b2c3d4\nn=uzak+oturum\nd=dev-B\n" +
            "@@F@@pa-e5f6a7b8\nn=\nd=dev-A\n"
        val terms = parseRemoteTerms(out)
        // "main" pa-* değil — elenir; iki pa-* oturumu sıralı döner.
        assertEquals(
            listOf(
                RemoteTerm("pa-a1b2c3d4", "uzak oturum", "dev-B"),
                RemoteTerm("pa-e5f6a7b8", null, "dev-A"),
            ),
            terms,
        )
    }

    @Test fun parseLiveWithoutRegistryKeepsTmuxName() {
        val terms = parseRemoteTerms("pa-12345678\n@@REG@@\n")
        assertEquals(listOf(RemoteTerm("pa-12345678", null, null)), terms)
    }

    @Test fun parseEmptyAndGarbage() {
        assertTrue(parseRemoteTerms("").isEmpty())
        assertTrue(parseRemoteTerms("no server running\n@@REG@@\n").isEmpty())
    }

    // ── SessionManager: registry + sahiplik ─────────────────────────────

    @Test fun freshOpenWritesRegistryFile() = runBlocking {
        val t = ExecRecordingTransport()
        val m = manager(t)
        m.open(conn, Secret.Password("pw"), customName = "web oturumu")
        awaitActive(m)
        withTimeout(5_000) {
            while (t.execs.none { it.contains(".pocket-agent/terms/pa-") }) delay(20)
        }
        val write = t.execs.first { it.contains(".pocket-agent/terms/pa-") }
        assertTrue(write.contains("'web+oturumu'"))
        assertTrue(write.contains("'dev-A'"))
        m.closeAll()
    }

    @Test fun adoptedOpenSkipsRegistryWrite() = runBlocking {
        val t = ExecRecordingTransport()
        val m = manager(t)
        // tmuxName verilmiş → reattach/adopt: mevcut registry ezilmez.
        m.open(conn, Secret.Password("pw"), forceNew = true, tmuxName = "pa-adopted1")
        awaitActive(m)
        delay(400)
        assertTrue(t.execs.none { it.contains(".pocket-agent/terms") })
        m.closeAll()
    }

    @Test fun sharedCloseDetachesWithoutKill() = runBlocking {
        val t = ExecRecordingTransport()
        val m = manager(t)
        m.open(conn, Secret.Password("pw"), forceNew = true, tmuxName = "pa-shared01", shared = true)
        awaitActive(m)
        m.close(m.sessions.value.single().id)
        delay(400)
        // Başka cihazın oturumu: kill-session GİTMEZ — sadece detach.
        assertTrue(t.execs.none { it.contains("kill-session") })
        assertTrue(m.sessions.value.isEmpty())
    }

    @Test fun ownCloseKillsAndRemovesRegistry() = runBlocking {
        val t = ExecRecordingTransport()
        val m = manager(t)
        m.open(conn, Secret.Password("pw"))
        awaitActive(m)
        m.close(m.sessions.value.single().id)
        withTimeout(5_000) {
            while (t.execs.none { it.contains("kill-session") }) delay(20)
        }
        val kill = t.execs.first { it.contains("kill-session") }
        assertTrue(kill.contains("rm -f"))
        assertTrue(kill.contains(".pocket-agent/terms/"))
    }

    @Test fun renameRefreshesRegistry() = runBlocking {
        val t = ExecRecordingTransport()
        val m = manager(t)
        m.open(conn, Secret.Password("pw"))
        awaitActive(m)
        withTimeout(5_000) { while (t.execs.none { it.contains("terms/pa-") }) delay(20) }
        t.execs.clear()
        m.rename(m.sessions.value.single().id, "yeni ad")
        withTimeout(5_000) {
            while (t.execs.none { it.contains("terms/pa-") }) delay(20)
        }
        assertTrue(t.execs.first { it.contains("terms/pa-") }.contains("'yeni+ad'"))
        m.closeAll()
    }

    @Test fun discoverRemoteListsOtherDevicesTerms() = runBlocking {
        val t = ExecRecordingTransport()
        t.execOut = "pa-own00001\npa-other001\n@@REG@@\n" +
            "@@F@@pa-other001\nn=uzak+is\n@@F@@pa-own00001\nn=benim\nd=dev-A\n"
        val m = manager(t)
        m.open(conn, Secret.Password("pw"), forceNew = true, tmuxName = "pa-own00001")
        awaitActive(m)
        m.discoverRemote()
        withTimeout(5_000) { while (m.remote.value.isEmpty()) delay(20) }
        // Yerelde açık olan pa-own00001 elenir; registry'siz ama canlı
        // pa-other001 ad+aygıtsız (null) listelenir.
        assertEquals(listOf(RemoteTerm("pa-other001", "uzak is", null)), m.remote.value["c1"])
        m.closeAll()
    }
}
