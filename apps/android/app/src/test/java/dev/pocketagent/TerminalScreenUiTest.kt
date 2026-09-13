// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import dev.pocketagent.transport.FakeSshTransport
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.SettingsViewModel
import dev.pocketagent.ui.TerminalChromeState
import dev.pocketagent.ui.TerminalScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Compose UI smoke testleri (Robolectric): ekranlar gerçek Compose tree ile
// render edilir; kritik metinler/düğmeler doğrulanır.
private class UiFakeConnector : SshConnector {
    override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
        FakeSshTransport().also { it.openPty("xterm-256color", size) }
}

@RunWith(RobolectricTestRunner::class)
class TerminalScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private fun manager(): SessionManager {
        val scope = CoroutineScope(Dispatchers.IO)
        val store = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        return SessionManager(scope, store) { UiFakeConnector() }
    }

    @Test fun emptyTerminalShowsHint() {
        rule.setContent {
            TerminalScreen(manager(), SettingsViewModel(), onNewConnection = {})
        }
        rule.onNodeWithText("açık oturum yok", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Yeni bağlantı").assertIsDisplayed()
    }

    @Test fun sessionChipAppearsOnConnect() {
        val m = manager()
        rule.setContent {
            TerminalScreen(m, SettingsViewModel(), {}, onFullscreenChange = {})
        }
        val conn = SavedConnection("sunucu", "h", 22, "u", "ram:password", id = "c1")
        m.open(conn, Secret.Password("pw"))
        rule.waitForIdle()
        // oturum çipi + terminal yüzeyi + alt tuş şeridi render edildi
        rule.onNodeWithText("sunucu").assertIsDisplayed()
        rule.onNodeWithContentDescription("Terminal çıktısı").assertIsDisplayed()
        rule.onNodeWithText("ctrl").assertIsDisplayed()
        rule.onNodeWithText("esc").assertIsDisplayed()
        m.closeAll()
    }

    @Test fun fullscreenKeepsKeyBarAndNotifies() {
        val m = manager()
        var fullscreenSeen: Boolean? = null
        // Aksiyonlar artık üst barda (PocketApp); izole testte chrome durumu
        // doğrudan sürülür — panel fullscreen'de kendi aksiyonlarını gösterir.
        val chrome = TerminalChromeState()
        rule.setContent {
            TerminalScreen(m, SettingsViewModel(), {}, onFullscreenChange = { fullscreenSeen = it }, chrome = chrome)
        }
        val conn = SavedConnection("sunucu", "h", 22, "u", "ram:password", id = "c1")
        m.open(conn, Secret.Password("pw"))
        rule.waitForIdle()
        rule.runOnIdle { chrome.fullscreen = true }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(true, fullscreenSeen)
        rule.onNodeWithText("ctrl").assertIsDisplayed()
        rule.onNodeWithText("esc").assertIsDisplayed()
        // Tam ekranda üst bar gizli → aksiyonlar panel başlığına döner.
        rule.onNodeWithContentDescription("Scrollback'te ara").assertIsDisplayed()
        rule.onNodeWithContentDescription("Scrollback'i paylaş").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tam ekrandan çık").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(false, fullscreenSeen)
        m.closeAll()
    }
}
