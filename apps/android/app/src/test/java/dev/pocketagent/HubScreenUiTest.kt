// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.data.ProfileRepository
import dev.pocketagent.transport.FakeSshTransport
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.HubScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Oturumlar (kök ekran): ilk açılışta kurulum rehberi; host varken liste;
// oturum kartı uzun basmayla adlandırılır; host'taki tmux'a attach; kapatma
// her zaman sorulur ve "ayır" tmux'u öldürmez.
@RunWith(RobolectricTestRunner::class)
class HubScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private class C : SshConnector {
        override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
            FakeSshTransport().also { it.openPty("xterm-256color", size) }
    }

    private fun manager(): SessionManager {
        val store = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        return SessionManager(CoroutineScope(Dispatchers.IO), store) { C() }
    }

    private val db by lazy {
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    private fun repo() = ConnectionRepository(db.connections())
    private fun profiles() = ProfileRepository(db.profiles())

    @Test fun firstRunShowsSetupGuide() {
        rule.setContent { HubScreen(manager(), repo(), profiles(), onOpenSession = {}) }
        rule.onNodeWithText("Cebindeki terminal").assertIsDisplayed()
        rule.onNodeWithText("pocket-agent onboard").assertIsDisplayed()
        rule.onNodeWithText("QR veya kodla eşle").assertIsDisplayed()
    }

    @Test fun savedHostsAreListed() {
        val r = repo()
        runBlocking {
            r.upsert(SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        }
        rule.setContent { HubScreen(manager(), r, profiles(), onOpenSession = {}) }
        rule.waitForIdle()
        rule.onNodeWithText("Hostlar").assertIsDisplayed()
        rule.onNodeWithText("prod").assertIsDisplayed()
        rule.onNodeWithText("root@10.0.0.5:22").assertIsDisplayed()
        rule.onNodeWithText("Cebindeki terminal").assertDoesNotExist()
    }

    @Test fun tappingHostOpensSession() {
        val r = repo()
        val m = manager()
        runBlocking {
            r.upsert(SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        }
        var opened = false
        rule.setContent { HubScreen(m, r, profiles(), onOpenSession = { opened = true }) }
        rule.waitForIdle()
        rule.onNodeWithText("prod").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertTrue(opened)
        org.junit.Assert.assertEquals(1, m.sessions.value.size)
        m.closeAll()
    }

    // Oturum kartına uzun bas → ad diyaloğu → yeni ad kartta görünür.
    @Test fun sessionCardLongPressRenamesSession() {
        val m = manager()
        rule.setContent { HubScreen(m, repo(), profiles(), onOpenSession = {}) }
        val conn = SavedConnection("sunucu", "h", 22, "u", "ram:password", id = "c1")
        m.open(conn, Secret.Password("pw"))
        rule.waitForIdle()
        rule.onNodeWithText("sunucu").assertIsDisplayed()

        rule.onNodeWithText("sunucu").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Oturum adı").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("prod-web")
        rule.onNodeWithText("Kaydet").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("prod-web").assertIsDisplayed()
        org.junit.Assert.assertEquals("prod-web", m.customNames.value[m.sessions.value.single().id])
        m.closeAll()
    }

    // Kapatma onayı: "Ayır" bağlantıyı kapatır ama host'taki tmux'u öldürmez.
    @Test fun closeAsksAndDetachKeepsRemoteTmux() {
        val probe = ExecRecordingTransport()
        val store = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        val m = SessionManager(CoroutineScope(Dispatchers.IO), store) {
            object : SshConnector {
                override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport {
                    probe.inner.openPty("xterm-256color", size)
                    return probe
                }
            }
        }
        rule.setContent { HubScreen(m, repo(), profiles(), onOpenSession = {}) }
        m.open(SavedConnection("sunucu", "h", 22, "u", "ram:password", id = "c1"), Secret.Password("pw"))
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Oturum seçenekleri").performClick()
        rule.waitForIdle()
        // Sayfa içi dokunuşlar semantik eylemle: Robolectric'te ModalBottomSheet
        // penceresine enjekte edilen dokunma güvenilir değil.
        rule.onNodeWithText("Oturumu kapat…").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("Sonlandır").assertIsDisplayed()
        rule.onNodeWithText("Ayır").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { m.sessions.value.isEmpty() }
        Thread.sleep(300)
        org.junit.Assert.assertTrue(probe.execs.none { it.contains("kill-session") })
    }

    // Cihazlar-arası keşif: host'taki registry'li pa-* oturumu "host'ta
    // çalışıyor" satırı olarak görünür; dokun → aynı tmux'a attach.
    @Test fun remoteSessionRowAttachesToSharedTmux() {
        val probe = ExecRecordingTransport().apply {
            execOut = "pa-own00001\npa-a1b2c3d4\n@@REG@@\n" +
                "@@F@@pa-a1b2c3d4\nn=uzak+is\nd=dev-B\n" +
                "@@F@@pa-own00001\nn=benim\nd=dev-A\n"
        }
        val store = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        val m = SessionManager(CoroutineScope(Dispatchers.IO), store) {
            object : SshConnector {
                override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport {
                    probe.inner.openPty("xterm-256color", size)
                    return probe
                }
            }
        }.apply { deviceId = "dev-A" }
        val r = repo()
        val conn = SavedConnection("sunucu", "h", 22, "u", "ram:password", id = "c1")
        runBlocking { r.upsert(conn, Secret.Password("x")) }
        m.open(conn, Secret.Password("x"), forceNew = true, tmuxName = "pa-own00001")
        rule.setContent { HubScreen(m, r, profiles(), onOpenSession = {}) }
        rule.waitForIdle()

        rule.onNodeWithText("uzak is").assertIsDisplayed()
        rule.onNodeWithText("pa-a1b2c3d4", substring = true).assertIsDisplayed()
        rule.onNodeWithText("uzak is").performClick()
        rule.waitForIdle()
        // Attach: ikinci oturum aynı tmux adıyla, paylaşımlı (kill'siz close).
        org.junit.Assert.assertEquals(2, m.sessions.value.size)
        org.junit.Assert.assertTrue(m.tmuxNames.value.values.toList().containsAll(listOf("pa-own00001", "pa-a1b2c3d4")))
        m.closeAll()
    }
}
