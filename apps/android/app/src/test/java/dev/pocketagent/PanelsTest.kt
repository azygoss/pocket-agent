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
    // SFTP+exec'li oturum açan manager (bulk/grep testleri ortak kurulumu).
    private fun sftpManager(
        transport: SftpFakeTransport,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = dev.pocketagent.transport.SessionManager(
        scope,
        dev.pocketagent.transport.TofuHostKeyStore(
            java.io.File.createTempFile("khst", null).apply { delete() },
        ),
    ) {
        object : dev.pocketagent.transport.SshConnector {
            override suspend fun open(
                c: dev.pocketagent.transport.SavedConnection,
                s: dev.pocketagent.transport.Secret?,
                size: dev.pocketagent.transport.TerminalSize,
            ): dev.pocketagent.transport.SshTransport {
                transport.openPty("xterm-256color", size)
                return transport
            }
        }
    }

    private suspend fun activeVm(
        mgr: dev.pocketagent.transport.SessionManager,
        scope: kotlinx.coroutines.CoroutineScope,
        cacheDir: java.io.File,
    ): FilesViewModel {
        val conn = dev.pocketagent.transport.SavedConnection(
            "t", "h", 22, "u", "ram:password", id = "c1",
        )
        val ctl = mgr.open(conn, dev.pocketagent.transport.Secret.Password("pw"))
        kotlinx.coroutines.withTimeout(5_000) {
            while (ctl.state.value != dev.pocketagent.transport.ConnectionState.ACTIVE) {
                kotlinx.coroutines.delay(10)
            }
        }
        val vm = FilesViewModel(mgr, scope, cacheDir)
        vm.open()
        kotlinx.coroutines.withTimeout(5_000) { while (vm.path == null) kotlinx.coroutines.delay(10) }
        assertEquals("/home/u", vm.path)
        return vm
    }

    @Test fun filesLongPressOps() = kotlinx.coroutines.runBlocking {
        // Uzun-basma aksiyonları: rename/delete/mkdir-inside SFTP'ye,
        // "terminale yaz" aktif PTY'ye shell-quote'lu gider.
        val transport = SftpFakeTransport()
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        val mgr = sftpManager(transport, scope)
        val vm = activeVm(mgr, scope, java.io.File.createTempFile("cache", "d"))

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

    @Test fun filesBulkAndGrep() = kotlinx.coroutines.runBlocking {
        // Çoklu seçim: toplu indir (cache'e yazar) + toplu sil.
        // İçerik arama: exec grep → parse → reveal (üst dizin + önizleme).
        val transport = SftpFakeTransport()
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        val mgr = sftpManager(transport, scope)
        val cacheDir = java.io.File.createTempFile("cache", "d")
            .apply { delete(); mkdirs() }
        val vm = activeVm(mgr, scope, cacheDir)

        val a = dev.pocketagent.transport.RemoteFile("a.txt", "/home/u/a.txt", false, 3, 0)
        val b = dev.pocketagent.transport.RemoteFile("b.txt", "/home/u/b.txt", false, 3, 0)
        transport.files[a.path] = byteArrayOf(1)
        transport.files[b.path] = byteArrayOf(2, 3)

        vm.downloadAll(listOf(a, b))
        kotlinx.coroutines.withTimeout(5_000) {
            while (vm.downloaded == null) kotlinx.coroutines.delay(10)
        }
        assertEquals(2, vm.downloaded!!.size)
        assertTrue(java.io.File(cacheDir, "shared/a.txt").exists())
        vm.clearDownloaded()

        vm.deleteAll(listOf(a, b))
        kotlinx.coroutines.withTimeout(5_000) {
            while (transport.deleted.size < 2) kotlinx.coroutines.delay(10)
        }
        assertFalse(transport.files.containsKey(a.path))
        assertFalse(transport.files.containsKey(b.path))

        transport.execResult = 0 to "/home/u/a.txt:7:needle\n/home/u/deep/c.txt:2:needle\n"
        vm.grep("needle")
        kotlinx.coroutines.withTimeout(5_000) {
            while (vm.grepResults == null) kotlinx.coroutines.delay(10)
        }
        assertEquals(2, vm.grepResults!!.size)
        assertEquals("/home/u/deep/c.txt", vm.grepResults!![1].path)
        assertEquals(2, vm.grepResults!![1].line)
        assertTrue(transport.execs.any { it.contains("grep -rInI") && it.contains("'needle'") })

        vm.revealPath("/home/u/deep/c.txt")
        kotlinx.coroutines.withTimeout(5_000) {
            while (vm.path != "/home/u/deep") kotlinx.coroutines.delay(10)
        }
        kotlinx.coroutines.withTimeout(5_000) {
            while (vm.preview == null) kotlinx.coroutines.delay(10)
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
