// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.ui.*
import org.junit.Assert.*
import org.junit.Test

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
    @Test fun filesJail() {
        val vm = FilesViewModel()
        assertTrue(vm.canOpen("docs/a.md"))
        assertFalse(vm.canOpen("../../etc/passwd"))
        assertFalse(vm.canOpen("/etc/passwd"))
    }
    @Test fun themeAndShortcuts() {
        val s = SettingsViewModel()
        val wasDark = s.theme.dark
        s.toggleDark(); assertNotEquals(wasDark, s.theme.dark)
        s.setFontScale(5f); assertEquals(2.0f, s.theme.fontScale)
        val sc = ShortcutModel()
        assertTrue(sc.add("htop", "Ctrl-t"))
        assertFalse(sc.add("dup", "Ctrl-t"))
        assertEquals("Ctrl-b", sc.tmuxPrefix())
    }
}
