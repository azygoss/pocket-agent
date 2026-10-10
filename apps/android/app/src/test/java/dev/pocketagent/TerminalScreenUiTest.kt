// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.FakeSshTransport
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
import dev.pocketagent.transport.TerminalInput
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

// Compose UI smoke testleri (Robolectric): çalışma alanı gerçek Compose
// ağacıyla render edilir; kritik metinler/düğmeler doğrulanır.
private class UiFakeConnector : SshConnector {
    var last: FakeSshTransport? = null
    override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
        FakeSshTransport().also { it.openPty("xterm-256color", size); last = it }
}

private class UiSftpConnector : SshConnector {
    override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
        SftpFakeTransport().also { it.openPty("xterm-256color", size) }
}

private class UiTunnelConnector : SshConnector {
    override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
        TunnelFakeTransport().also { it.openPty("xterm-256color", size) }
}

@RunWith(RobolectricTestRunner::class)
class TerminalScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private fun manager(connector: SshConnector = UiFakeConnector()): SessionManager {
        val scope = CoroutineScope(Dispatchers.IO)
        val store = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        return SessionManager(scope, store) { connector }
    }

    private val conn = SavedConnection("sunucu", "h", 22, "u", "ram:password", id = "c1")

    private fun openActive(m: SessionManager) {
        m.open(conn, Secret.Password("pw"))
        rule.waitUntil(5_000) { m.sessions.value.single().controller.state.value == ConnectionState.ACTIVE }
        rule.waitForIdle()
    }

    @Test fun emptyTerminalShowsHint() {
        rule.setContent {
            TerminalScreen(manager(), SettingsViewModel(), onNewConnection = {})
        }
        rule.onNodeWithText("açık oturum yok", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Yeni bağlantı").assertIsDisplayed()
    }

    @Test fun sessionAppearsOnConnect() {
        val m = manager()
        rule.setContent { TerminalScreen(m, SettingsViewModel(), {}, onFullscreenChange = {}) }
        openActive(m)
        // oturum başlığı + terminal yüzeyi + tuş paneli render edildi
        rule.onNodeWithText("sunucu").assertIsDisplayed()
        rule.onNodeWithContentDescription("Terminal çıktısı").assertIsDisplayed()
        rule.onNodeWithText("ctrl").assertIsDisplayed()
        rule.onNodeWithText("esc").assertIsDisplayed()
        m.closeAll()
    }

    // Agent istemleri için klavyesiz yanıt: Enter ve Shift+Tab tuşları.
    @Test fun enterAndShiftTabKeysReachPty() {
        val connector = UiFakeConnector()
        val m = manager(connector)
        rule.setContent { TerminalScreen(m, SettingsViewModel(), {}) }
        openActive(m)
        rule.onNodeWithContentDescription("Enter").performClick()
        rule.onNodeWithContentDescription("Shift Tab").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            val sent = connector.last?.sent.orEmpty().filterIsInstance<TerminalInput.Text>().map { it.s }
            "\r" in sent && "\u001B[Z" in sent
        }
        m.closeAll()
    }

    @Test fun fullscreenKeepsKeyBarAndNotifies() {
        val m = manager()
        var fullscreenSeen: Boolean? = null
        val chrome = TerminalChromeState()
        rule.setContent {
            TerminalScreen(m, SettingsViewModel(), {}, onFullscreenChange = { fullscreenSeen = it }, chrome = chrome)
        }
        openActive(m)
        rule.runOnIdle { chrome.fullscreen = true }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(true, fullscreenSeen)
        rule.onNodeWithText("ctrl").assertIsDisplayed()
        rule.onNodeWithText("esc").assertIsDisplayed()
        // Tam ekranda çubuk gizli → eylemler sağ üst kapsülde.
        rule.onNodeWithContentDescription("Scrollback'te ara").assertIsDisplayed()
        rule.onNodeWithContentDescription("Scrollback'i paylaş").assertIsDisplayed()
        rule.onNodeWithContentDescription("Tam ekrandan çık").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(false, fullscreenSeen)
        m.closeAll()
    }

    @Test fun attachButtonDisabledWithoutSftp() {
        // FakeSshTransport SFTP uygulamaz → ek düğmesi görünür ama pasif.
        val m = manager()
        rule.setContent { TerminalScreen(m, SettingsViewModel(), {}, onFullscreenChange = {}) }
        openActive(m)
        rule.onNodeWithContentDescription("Dosya ekle").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Dosyalar").assertIsNotEnabled()
        m.closeAll()
    }

    @Test fun attachButtonEnabledWithSftp() {
        val m = manager(UiSftpConnector())
        rule.setContent { TerminalScreen(m, SettingsViewModel(), {}, onFullscreenChange = {}) }
        openActive(m)
        rule.onNodeWithContentDescription("Dosya ekle").assertIsEnabled()
        rule.onNodeWithContentDescription("Dosyalar").assertIsEnabled()
        m.closeAll()
    }

    @Test fun previewDisabledWithoutTunnel() {
        // FakeSshTransport TcpipCapable uygulamaz → menü öğesi pasif.
        val m = manager()
        rule.setContent { TerminalScreen(m, SettingsViewModel(), {}, onFullscreenChange = {}) }
        openActive(m)
        rule.onNodeWithContentDescription("Diğer").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Web önizleme").assertIsNotEnabled()
        m.closeAll()
    }

    @Test fun previewOpensPortPicker() {
        // Tünel+exec fake'i: sayfa açılır, ss sondası 8080'i listeler.
        val m = manager(UiTunnelConnector())
        rule.setContent { TerminalScreen(m, SettingsViewModel(), {}, onFullscreenChange = {}) }
        openActive(m)
        rule.onNodeWithContentDescription("Diğer").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Web önizleme").assertIsEnabled().performClick()
        rule.waitForIdle()
        // ss/netstat bulguları "localhost" hedefiyle listelenir.
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("localhost:8080")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("localhost:8080").assertIsDisplayed()
        m.closeAll()
    }

    // Kapatma her zaman sorulur; "Sonlandır" oturumu listeden düşürür.
    @Test fun closeFromMenuAsksFirst() {
        val m = manager()
        var collapsed = false
        rule.setContent { TerminalScreen(m, SettingsViewModel(), {}, onCollapse = { collapsed = true }) }
        openActive(m)
        rule.onNodeWithContentDescription("Diğer").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Oturumu kapat…").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals("onaydan önce kapanmamalı", 1, m.sessions.value.size)
        rule.onNodeWithText("Sonlandır").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { m.sessions.value.isEmpty() }
        rule.waitForIdle()
        org.junit.Assert.assertTrue(collapsed)
    }

    // Ekranı açık tut: taşma menüsünden açılır, ayara yazılır.
    @Test fun keepScreenOnToggleFromMenu() {
        val m = manager()
        val vm = SettingsViewModel()
        rule.setContent { TerminalScreen(m, vm, {}) }
        openActive(m)
        rule.onNodeWithContentDescription("Diğer").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ekranı açık tut").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertTrue(vm.keepScreenOn)
        m.closeAll()
    }

    // Ok tuşunu basılı tutmak tekrar eder (agent seçim listelerinde gezinme);
    // kısa dokunuş tek basış.
    @Test fun holdingArrowRepeats() {
        val connector = UiFakeConnector()
        val m = manager(connector)
        rule.setContent { TerminalScreen(m, SettingsViewModel(), {}) }
        openActive(m)
        fun ups() = connector.last?.sent.orEmpty().filterIsInstance<TerminalInput.Text>().count { it.s == "\u001B[A" }
        rule.onNodeWithContentDescription("Yukarı ok").performClick()
        rule.waitUntil(3_000) { ups() == 1 }
        rule.onNodeWithContentDescription("Yukarı ok").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        rule.waitUntil(3_000) { ups() >= 5 }
        m.closeAll()
    }

    // Kabuk bracketed paste açmadıysa çok satırlı yapıştırma her satırı
    // komut olarak çalıştırabilir: önce sorulur, onaylanınca gider.
    @Test fun multilinePasteAsksWhenShellCannotTellPaste() {
        val connector = UiFakeConnector()
        val m = manager(connector)
        rule.setContent { TerminalScreen(m, SettingsViewModel(), {}) }
        openActive(m)
        val cm = org.robolectric.RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("t", "ls\nrm -rf build"))
        rule.onNodeWithContentDescription("Yapıştır").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("2 satır yapıştırılsın mı?").assertExists()
        fun pasted() = connector.last?.sent.orEmpty().filterIsInstance<TerminalInput.Text>().any { it.s.contains("rm -rf build") }
        org.junit.Assert.assertFalse(pasted())
        rule.onNodeWithText("Yapıştır").performClick()
        rule.waitUntil(3_000) { pasted() }
        m.closeAll()
    }
}
