// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.FakeSshTransport
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
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
        override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
            FakeSshTransport().also { it.openPty("xterm-256color", size) }
    }

    private fun manager(): SessionManager {
        val store = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        return SessionManager(CoroutineScope(Dispatchers.IO), store) { C() }
    }

    private fun repo(): ConnectionRepository {
        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        return ConnectionRepository(db.connections())
    }

    @Test fun emptyConnectionsShowsFirstRunWizard() {
        rule.setContent { ConnectionsScreen(repo(), manager()) {} }
        rule.onNodeWithText("Hızlı kurulum").assertExists()
        rule.onNodeWithText("pocket-agent onboard").assertExists()
        rule.onNodeWithText("QR / kod ile eşle").assertExists()
    }

    @Test fun savedConnectionShowsCard() {
        val r = repo()
        runBlocking {
            r.upsert(SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        }
        rule.setContent { ConnectionsScreen(r, manager()) {} }
        rule.waitForIdle()
        rule.onNodeWithText("prod").assertIsDisplayed()
        rule.onNodeWithText("root@10.0.0.5:22").assertIsDisplayed()
    }

    @Test fun filesWithoutSessionShowsHint() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val files = FilesViewModel(manager(), scope, File.createTempFile("cache", null).parentFile!!)
        rule.setContent { FilesScreen(files) }
        rule.onNodeWithText("Aktif bir SSH oturumu yok", substring = true).assertIsDisplayed()
    }
}
