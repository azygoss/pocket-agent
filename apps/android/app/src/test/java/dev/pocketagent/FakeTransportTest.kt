// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FakeTransportTest {
    @Test fun echoAndResize() = runBlocking {
        val t = FakeSshTransport()
        t.openPty("xterm-256color", TerminalSize(80, 24))
        assertTrue(t.read().bytes.decodeToString().contains("80x24"))
        t.send(TerminalInput.Text("whoami\n"))
        assertEquals("> whoami\n", t.read().bytes.decodeToString())
        t.resize(TerminalSize(100, 30))
        assertEquals(listOf(TerminalSize(100, 30)), t.resizes)
        assertTrue(t.read().bytes.decodeToString().contains("100x30"))
        t.close()
    }
    @Test fun viewModelBoundsScrollbackAndBadge() {
        val vm = TerminalViewModel(SessionId("s1"), maxLines = 5000)
        vm.onFrame(TerminalFrame("a".toByteArray(), TerminalTransport.MOSH))
        assertEquals("MOSH", vm.badge)
        // 6000 satır besle → scrollback 5000 ile sınırlanır; + 23 görünen ekran satırı
        vm.onFrame(TerminalFrame("x\n".repeat(6000).toByteArray(), TerminalTransport.SSH))
        assertEquals(5000 + 23, vm.frames.value.size)
        assertEquals(5000 + 23, vm.lines.value.size)
        vm.grow(); assertEquals(90, vm.size.cols)
        vm.shrink(); vm.shrink(); assertEquals(70, vm.size.cols)
    }
}
