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
