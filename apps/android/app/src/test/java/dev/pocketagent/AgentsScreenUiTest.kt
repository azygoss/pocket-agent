// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.pocketagent.ui.AgentsScreen
import dev.pocketagent.ui.ApprovalViewModel
import dev.pocketagent.ui.InboxRow
import dev.pocketagent.ui.InboxViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Agents ekranı smoke: boş durum + satır render + onay düğmeleri.
@RunWith(RobolectricTestRunner::class)
class AgentsScreenUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyInboxShowsEmptyState() {
        val app = RuntimeEnvironment.getApplication() as dev.pocketagent.android.App
        rule.setContent {
            AgentsScreen(inbox = InboxViewModel(), approval = ApprovalViewModel(), app = app)
        }
        rule.waitForIdle()
        // boş durum metni (AgentsScreen içindeki empty-state copy)
        rule.onNodeWithText("Aktif agent olayı yok").assertIsDisplayed()
    }

    @Test fun approvalRowShowsActions() {
        val app = RuntimeEnvironment.getApplication() as dev.pocketagent.android.App
        val inbox = InboxViewModel()
        inbox.mergeRemote(
            listOf(
                dev.pocketagent.net.BackendEvent(
                    eventId = "event:e1", host = "host:1", session = "sess:1",
                    source = "codex", category = "APPROVAL_REQUIRED",
                    message = "Deploy to prod?", digest = "d", revision = "1",
                    createdAt = "2026-09-03T12:00:00Z", expiresAt = "",
                ),
            ),
        )
        rule.setContent {
            AgentsScreen(inbox = inbox, approval = ApprovalViewModel(), app = app)
        }
        rule.waitForIdle()
        rule.onNodeWithText("Deploy to prod?").assertIsDisplayed()
        rule.onNodeWithText("Onayla").assertIsDisplayed()
        rule.onNodeWithText("Reddet").assertIsDisplayed()
    }
}
