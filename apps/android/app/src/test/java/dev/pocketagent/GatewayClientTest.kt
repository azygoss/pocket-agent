// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.net.GatewayClient
import dev.pocketagent.transport.ChatBlock
import dev.pocketagent.transport.SessionId
import dev.pocketagent.ui.ChatViewModel
import org.junit.Assert.*
import org.junit.Test

class GatewayClientTest {
    @Test fun rejectsTraversalAndSsrf() {
        val g = GatewayClient(24543, "t")
        try { g.fileUrl("../../etc/passwd"); fail() } catch (_: IllegalArgumentException) {}
        try { g.previewUrl("example.com", 80, "/"); fail() } catch (_: IllegalArgumentException) {}
        assertTrue(g.fileUrl("docs/a.md").toString().contains("127.0.0.1:24543"))
    }
    @Test fun chatSharesSessionAndRedirects() {
        val vm = ChatViewModel(SessionId("session:x"))
        vm.append(ChatBlock.Message("hi"))
        vm.append(ChatBlock.UnsupportedRedirectToTerminal)
        assertEquals(2, vm.blocks.value.size)
        assertTrue(vm.needsTerminal(vm.blocks.value[1]))
        assertFalse(vm.needsTerminal(vm.blocks.value[0]))
    }
}
