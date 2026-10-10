// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.data.ProfileRepository
import dev.pocketagent.transport.FakeSshTransport
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.SavedProfile
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
import dev.pocketagent.transport.TerminalInput
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.FilesScreen
import dev.pocketagent.ui.FilesViewModel
import dev.pocketagent.ui.HubScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Hızlı başlat profilleri + Dosyalar smoke testleri (Robolectric Compose).
@RunWith(RobolectricTestRunner::class)
class ConnectionsFilesUiTest {
    @get:Rule val rule = createComposeRule()

    private class C : SshConnector {
        var last: FakeSshTransport? = null
        override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
            FakeSshTransport().also { it.openPty("xterm-256color", size); last = it }
    }

    private fun manager(connector: C = C()): SessionManager {
        val store = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        return SessionManager(CoroutineScope(Dispatchers.IO), store) { connector }
    }

    private fun db(): AppDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), AppDatabase::class.java,
    ).allowMainThreadQueries().build()

    // Sade ana sayfa: profil yokken "Hızlı başlat" bölümü yer kaplamaz;
    // profil eklemek tek "Ekle" girişinden.
    @Test fun quickLaunchProfileIsAddedFromAddSheet() {
        val db = db()
        val r = ConnectionRepository(db.connections())
        runBlocking {
            r.upsert(SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        }
        rule.setContent { hub(r, ProfileRepository(db.profiles()), manager()) }
        rule.waitForIdle()
        rule.onNodeWithText("Hızlı başlat").assertDoesNotExist()
        rule.onNodeWithContentDescription("Ekle").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("QR veya kodla eşle").assertExists()
        rule.onNodeWithText("Hızlı başlat profili").assertExists()
    }

    @Test fun profileTapOpensSessionAndSendsCommand() {
        val db = db()
        val r = ConnectionRepository(db.connections())
        val pr = ProfileRepository(db.profiles())
        val connector = C()
        val m = manager(connector)
        runBlocking {
            val cid = r.upsert(SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password"), Secret.Password("x"))
            pr.upsert(SavedProfile("Codex", "codex", connectionId = cid))
        }
        rule.setContent { hub(r, pr, m) }
        rule.waitForIdle()
        rule.onNodeWithText("Codex").assertIsDisplayed()
        rule.onNodeWithText("Codex").performClick()
        // Profil → yeni oturum açılır ve komut açılış dizisine eklenir.
        rule.waitUntil(10_000) { m.sessions.value.isNotEmpty() }
        rule.waitUntil(10_000) {
            connector.last?.sent?.any { it is TerminalInput.Text && it.s.contains("codex") } == true
        }
    }

    @Test fun unboundProfileAsksForHost() {
        val db = db()
        val r = ConnectionRepository(db.connections())
        val pr = ProfileRepository(db.profiles())
        runBlocking {
            r.upsert(SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password"), Secret.Password("x"))
            pr.upsert(SavedProfile("htop", "htop")) // host bağlı değil
        }
        rule.setContent { hub(r, pr, manager()) }
        rule.waitForIdle()
        rule.onNodeWithText("htop").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Bu profil hangi host'ta açılsın?", substring = true).assertIsDisplayed()
    }

    @Test fun filesWithoutSessionShowsHint() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val files = FilesViewModel(manager(), scope, File.createTempFile("cache", null).parentFile!!)
        rule.setContent { FilesScreen(files) }
        rule.onNodeWithText("Aktif bir SSH oturumu yok", substring = true).assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun hub(r: ConnectionRepository, p: ProfileRepository, m: SessionManager) =
        HubScreen(sessions = m, repo = r, profiles = p, onOpenSession = {})

    // Okuyucuda arama: eşleşme sayısı görünür, ileri/geri gezilir.
    @Test fun textViewerFindsInFile() {
        val transport = SftpFakeTransport()
        transport.files["/home/u/agent.log"] = (1..60).joinToString("\n") { if (it == 12 || it == 48) "ERROR needle $it" else "ok $it" }.toByteArray()
        val m = SessionManager(CoroutineScope(Dispatchers.IO), TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))) {
            object : SshConnector {
                override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport {
                    transport.openPty("xterm-256color", size)
                    return transport
                }
            }
        }
        val ctl = m.open(SavedConnection("t", "h", 22, "u", "ram:password", id = "c1"), Secret.Password("pw"))
        runBlocking {
            kotlinx.coroutines.withTimeout(5_000) {
                while (ctl.state.value != dev.pocketagent.transport.ConnectionState.ACTIVE) kotlinx.coroutines.delay(10)
            }
        }
        val files = FilesViewModel(m, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), File.createTempFile("cache", null).parentFile!!)
        rule.setContent { FilesScreen(files) }
        rule.waitForIdle()
        files.revealPath("/home/u/agent.log")
        rule.waitUntil(5_000) { files.preview != null }
        rule.onNodeWithContentDescription("Metinde ara").performClick()
        rule.waitForIdle()
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).onLast().performTextInput("needle")
        rule.waitForIdle()
        rule.onNodeWithText("1/2").assertExists()
        rule.onNodeWithContentDescription("Sonraki eşleşme").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("2/2").assertExists()
        m.closeAll()
    }
}
