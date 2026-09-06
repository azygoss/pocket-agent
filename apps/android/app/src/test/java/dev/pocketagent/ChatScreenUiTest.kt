// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.pocketagent.net.ChatBlockDto
import dev.pocketagent.ui.ChatDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// P14 sohbet görünümü: roller doğru render edilir (mesaj balonu, tool çipi,
// sonuç/hata satırı).
@RunWith(RobolectricTestRunner::class)
class ChatScreenUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun blocksRenderByRole() {
        val blocks = listOf(
            ChatBlockDto("message", "tool_running", "Testleri çalıştırıyorum"),
            ChatBlockDto("tool", "tool_running", "tool:Bash"),
            ChatBlockDto("result", "task_complete", "result:success"),
            ChatBlockDto("error", "error", "result:failure"),
        )
        rule.setContent { ChatDialog("session.jsonl", blocks) {} }
        rule.onNodeWithText("session.jsonl").assertIsDisplayed()
        rule.onNodeWithText("Testleri çalıştırıyorum").assertIsDisplayed()
        rule.onNodeWithText("Bash").assertIsDisplayed() // tool: prefix'i soyulur
        rule.onNodeWithText("success").assertIsDisplayed()
        rule.onNodeWithText("failure").assertIsDisplayed()
    }

    @Test fun emptyBlocksShowsHint() {
        rule.setContent { ChatDialog("bos.jsonl", emptyList()) {} }
        rule.onNodeWithText("Blok yok", substring = true).assertIsDisplayed()
    }
}
