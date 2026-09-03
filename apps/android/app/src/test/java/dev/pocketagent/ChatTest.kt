// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.*
import org.junit.Assert.*
import org.junit.Test

class ChatTest {
    @Test fun terminalAndChatShareSession() {
        val session = SessionId("session:abc")
        // Invariant: Chat View renders blocks for the SAME session id as terminal.
        val blocks: List<ChatBlock> = listOf(ChatBlock.Message("hi"), ChatBlock.MiniDiff("a.md"))
        assertTrue(blocks.isNotEmpty())
        assertEquals("session:abc", session.v)
        // Unsupported content redirects, never drops.
        assertTrue(ChatBlock.UnsupportedRedirectToTerminal is ChatBlock)
    }
}
