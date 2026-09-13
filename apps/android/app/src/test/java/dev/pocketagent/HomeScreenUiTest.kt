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
import dev.pocketagent.ui.HomeScreen
import dev.pocketagent.ui.InboxViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Home ekranı: host yokken onboarding adımları; host varken "Son bağlantılar"
// hızlı bağlan listesi (onboarding sonsuza dek gösterilmez — anti-slop).
@RunWith(RobolectricTestRunner::class)
class HomeScreenUiTest {
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

    @Test fun noHostsShowsOnboarding() {
        rule.setContent { HomeScreen(manager(), repo(), InboxViewModel()) {} }
        rule.onNodeWithText("Başlangıç").assertIsDisplayed()
        rule.onNodeWithText("İlk hostu ekle").assertIsDisplayed()
    }

    @Test fun savedHostsShowQuickConnect() {
        val r = repo()
        runBlocking {
            r.upsert(
                SavedConnection("prod", "10.0.0.5", 22, "root", "ram:password", id = "c1"),
                Secret.Password("x"),
            )
        }
        rule.setContent { HomeScreen(manager(), r, InboxViewModel()) {} }
        rule.waitForIdle()
        rule.onNodeWithText("Son bağlantılar").assertIsDisplayed()
        rule.onNodeWithText("prod").assertIsDisplayed()
        rule.onNodeWithText("root@10.0.0.5:22").assertIsDisplayed()
        rule.onNodeWithText("Başlangıç").assertDoesNotExist()
    }
}
