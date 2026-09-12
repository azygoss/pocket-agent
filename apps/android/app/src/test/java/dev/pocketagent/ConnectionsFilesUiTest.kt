// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import dev.pocketagent.ui.ConnectionsScreen
import dev.pocketagent.ui.FilesScreen
import dev.pocketagent.ui.FilesViewModel
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

// Connections/Files ekranı smoke testleri (Robolectric Compose).
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

    private fun repo(db: AppDatabase = db()) = ConnectionRepository(db.connections())

    @Test fun emptyConnectionsShowsFirstRunWizard() {
        val db = db()
        rule.setContent {
            ConnectionsScreen(ConnectionRepository(db.connections()), ProfileRepository(db.profiles()), manager()) {}
        }
        rule.onNodeWithText("Hızlı kurulum").assertExists()
        rule.onNodeWithText("pocket-agent onboard").assertExists()
        rule.onNodeWithText("QR / kod ile eşle").assertExists()
    }

    @Test fun savedConnectionShowsCard() {
        val db = db()
        val r = repo(db)
        runBlocking {
            r.upsert(SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        }
        rule.setContent { ConnectionsScreen(r, ProfileRepository(db.profiles()), manager()) {} }
        rule.waitForIdle()
        rule.onNodeWithText("Profiller").assertIsDisplayed()
        rule.onNodeWithText("prod").assertIsDisplayed()
        rule.onNodeWithText("root@10.0.0.5:22").assertIsDisplayed()
    }

    @Test fun profileTapOpensSessionAndSendsCommand() {
        val db = db()
        val r = repo(db)
        val pr = ProfileRepository(db.profiles())
        val connector = C()
        val m = manager(connector)
        runBlocking {
            val cid = r.upsert(
                SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password"),
                Secret.Password("x"),
            )
            pr.upsert(SavedProfile("Codex", "codex", connectionId = cid))
        }
        rule.setContent { ConnectionsScreen(r, pr, m) {} }
        rule.waitForIdle()
        rule.onNodeWithText("Codex").assertIsDisplayed()
        rule.onNodeWithText("Codex").performClick()
        // Profil → yeni oturum açılır ve komut 600ms gecikmeyle gönderilir.
        rule.waitUntil(10_000) { m.sessions.value.isNotEmpty() }
        rule.waitUntil(10_000) {
            connector.last?.sent?.any { it is TerminalInput.Text && it.s.contains("codex") } == true
        }
    }

    @Test fun unboundProfileAsksForHost() {
        val db = db()
        val r = repo(db)
        val pr = ProfileRepository(db.profiles())
        runBlocking {
            r.upsert(SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password"), Secret.Password("x"))
            pr.upsert(SavedProfile("htop", "htop")) // host bağlı değil
        }
        rule.setContent { ConnectionsScreen(r, pr, manager()) {} }
        rule.waitForIdle()
        rule.onNodeWithText("htop").performClick()
        rule.onNodeWithText("Bu profil hangi host'ta açılsın?", substring = true).assertIsDisplayed()
    }

    @Test fun filesWithoutSessionShowsHint() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val files = FilesViewModel(manager(), scope, File.createTempFile("cache", null).parentFile!!)
        rule.setContent { FilesScreen(files) }
        rule.onNodeWithText("Aktif bir SSH oturumu yok", substring = true).assertIsDisplayed()
    }
}
