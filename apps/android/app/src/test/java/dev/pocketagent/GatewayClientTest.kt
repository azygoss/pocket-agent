// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.net.GatewayClient
import dev.pocketagent.transport.ChatBlock
import dev.pocketagent.transport.GatewayTunnel
import dev.pocketagent.transport.SessionId
import dev.pocketagent.ui.ChatViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GatewayClientTest {
    private val fakeTunnel = object : GatewayTunnel {
        override suspend fun gatewayGet(path: String, token: String, maxBytes: Int): Pair<Int, ByteArray> =
            200 to "[]".toByteArray()
    }

    @Test fun rejectsTraversal() {
        val g = GatewayClient(fakeTunnel, "t")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { g.readFile("../../etc/passwd") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { g.ls("a/../b") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { g.diff("staged; rm -rf /") } // sabit küme dışı
        }
    }

    @Test fun parsesLsJson() {
        val rows = GatewayClient.parseLs("""[{"name":"src","dir":true,"size":0,"mtime":10},{"name":"a.md","dir":false,"size":5,"mtime":20}]""")
        assertEquals(2, rows.size)
        assertTrue(rows[0].isDir)
        assertEquals("a.md", rows[1].name)
        assertEquals(5, rows[1].size)
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
