// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.ui.*
import org.junit.Assert.*
import org.junit.Test

private class PanelsFakeConnector : dev.pocketagent.transport.SshConnector {
    override suspend fun open(
        conn: dev.pocketagent.transport.SavedConnection,
        secret: dev.pocketagent.transport.Secret?,
        size: dev.pocketagent.transport.TerminalSize,
    ): dev.pocketagent.transport.SshTransport =
        dev.pocketagent.transport.FakeSshTransport().also { it.openPty("xterm-256color", size) }
}

class PanelsTest {
    @Test fun inboxMergesPerSession() {
        val vm = InboxViewModel()
        vm.add("s1", "e1", "Run tests?")
        vm.add("s1", "e2", "Deploy?")
        assertEquals(1, vm.rows.size)
        assertEquals("e2", vm.rows[0].eventId)
        vm.markRead("e2")
        assertFalse(vm.rows[0].unread)
    }
    @Test fun approvalRequiresDigest() {
        val vm = ApprovalViewModel()
        assertFalse(vm.decide("", "3", true))
        assertTrue(vm.decide("d", "3", true))
        assertEquals("approve", vm.lastDecision)
    }
    @Test fun filesRequiresActiveSession() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val mgr = dev.pocketagent.transport.SessionManager(
            scope,
            dev.pocketagent.transport.TofuHostKeyStore(java.io.File.createTempFile("hostkeys", ".db")),
        ) { PanelsFakeConnector() }
        val vm = FilesViewModel(mgr, scope, java.io.File.createTempFile("cache", "dir"))
        assertFalse(vm.hasActiveSftp())
        vm.open() // oturum yok: no-op, crash yok
        assertNull(vm.path)
        assertTrue(vm.entries.isEmpty())
    }
    @Test fun filesLongPressOps() = kotlinx.coroutines.runBlocking {
        // Uzun-basma aksiyonları: rename/delete/mkdir-inside SFTP'ye,
        // "terminale yaz" aktif PTY'ye shell-quote'lu gider.
        val transport = SftpFakeTransport()
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        val store = dev.pocketagent.transport.TofuHostKeyStore(
            java.io.File.createTempFile("khst", null).apply { delete() },
        )
        val connector = object : dev.pocketagent.transport.SshConnector {
            override suspend fun open(
                c: dev.pocketagent.transport.SavedConnection,
                s: dev.pocketagent.transport.Secret?,
                size: dev.pocketagent.transport.TerminalSize,
            ): dev.pocketagent.transport.SshTransport {
                transport.openPty("xterm-256color", size)
                return transport
            }
        }
        val mgr = dev.pocketagent.transport.SessionManager(scope, store) { connector }
        val conn = dev.pocketagent.transport.SavedConnection(
            "t", "h", 22, "u", "ram:password", id = "c1",
        )
        val ctl = mgr.open(conn, dev.pocketagent.transport.Secret.Password("pw"))
        kotlinx.coroutines.withTimeout(5_000) {
            while (ctl.state.value != dev.pocketagent.transport.ConnectionState.ACTIVE) {
                kotlinx.coroutines.delay(10)
            }
        }
        val vm = FilesViewModel(mgr, scope, java.io.File.createTempFile("cache", "d"))
        vm.open()
        kotlinx.coroutines.withTimeout(5_000) { while (vm.path == null) kotlinx.coroutines.delay(10) }
        assertEquals("/home/u", vm.path)

        val f = dev.pocketagent.transport.RemoteFile("a.txt", "/home/u/a.txt", false, 3, 0)
        transport.files[f.path] = byteArrayOf(1)

        vm.rename(f, "b.txt")
        kotlinx.coroutines.withTimeout(5_000) {
            while (transport.renamed.isEmpty()) kotlinx.coroutines.delay(10)
        }
        assertEquals("/home/u/a.txt" to "/home/u/b.txt", transport.renamed[0])

        vm.delete(f)
        kotlinx.coroutines.withTimeout(5_000) {
            while (transport.deleted.isEmpty()) kotlinx.coroutines.delay(10)
        }
        assertFalse(transport.files.containsKey("/home/u/a.txt"))

        val dir = dev.pocketagent.transport.RemoteFile("sub", "/home/u/sub", true, 0, 0)
        vm.mkdir("nested", inside = dir)
        kotlinx.coroutines.withTimeout(5_000) {
            while (vm.path != "/home/u/sub") kotlinx.coroutines.delay(10)
        }
        assertTrue(transport.dirs.contains("/home/u/sub/nested"))

        assertTrue(vm.pastePathToTerminal(dir))
        kotlinx.coroutines.withTimeout(5_000) {
            while (transport.inner.sent.none {
                it is dev.pocketagent.transport.TerminalInput.Text && it.s == "'/home/u/sub'"
            }) {
                kotlinx.coroutines.delay(10)
            }
        }
        mgr.closeAll()
    }
    @Test fun themeAndShortcuts() {
        val s = SettingsViewModel()
        val wasTheme = s.theme.themeId
        s.setThemeId("dracula"); assertNotEquals(wasTheme, s.theme.themeId)
        s.setFontId("plex"); assertEquals("plex", s.theme.fontId)
        s.setFontScale(5f); assertEquals(2.0f, s.theme.fontScale)
        val sc = ShortcutModel()
        assertTrue(sc.add("htop", "Ctrl-t"))
        assertFalse(sc.add("dup", "Ctrl-t"))
        assertEquals("Ctrl-b", sc.tmuxPrefix())
    }
}
