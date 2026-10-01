// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.data.ProfileRepository
import dev.pocketagent.net.ChatBlockDto
import dev.pocketagent.transport.FakeSshTransport
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.ChatDialog
import dev.pocketagent.ui.ConnectionsScreen
import dev.pocketagent.ui.FilesScreen
import dev.pocketagent.ui.FilesViewModel
import dev.pocketagent.ui.HomeScreen
import dev.pocketagent.ui.SettingsScreen
import dev.pocketagent.ui.SettingsViewModel
import dev.pocketagent.ui.TerminalScreen
import dev.pocketagent.ui.UsageViewModel
import dev.pocketagent.ui.theme.ConsoleTheme
import dev.pocketagent.ui.theme.LocalTokens
import dev.pocketagent.ui.theme.PocketAgentTheme
import dev.pocketagent.ui.theme.PocketConsoleTheme
import dev.pocketagent.ui.theme.PocketPaperTheme
import dev.pocketagent.ui.theme.SheetInset
import dev.pocketagent.ui.theme.SheetShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// Tasarım görüntüleri (docs/design.md doğrulaması): PA_SHOTS_DIR verilirse
// ekranlar native grafikle render edilip PNG olarak yazılır. Kapı yoksa
// atlanır — CI'da çalışmaz, yalnız tasarım incelemesi için.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class DesignShotsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = System.getenv("PA_SHOTS_DIR")?.let(::File)

    @Before fun gate() = assumeTrue(out != null)



    private class C : SshConnector {
        override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
            FakeSshTransport().also { it.openPty("xterm-256color", size) }
    }

    private fun manager() = SessionManager(
        CoroutineScope(Dispatchers.IO),
        TofuHostKeyStore(File.createTempFile("hostkeys", ".db")),
    ) { C() }

    private fun db() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), AppDatabase::class.java,
    ).allowMainThreadQueries().build()

    private fun seeded(db: AppDatabase): ConnectionRepository {
        val r = ConnectionRepository(db.connections())
        runBlocking {
            r.upsert(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1", lastConnectedAt = System.currentTimeMillis() - 120_000), Secret.Password("x"))
            r.upsert(SavedConnection("build-box", "192.168.1.40", 22, "berk", "ram:password", id = "c2", lastConnectedAt = System.currentTimeMillis() - 7_200_000), Secret.Password("x"))
            r.upsert(SavedConnection("pi-garage", "pi.local", 2222, "pi", "ram:password", id = "c3"), null)
        }
        return r
    }

    // Uygulama kabuğunu taklit eder: chrome zemini + 8dp içeride sheet.
    @Composable
    private fun Shell(theme: ConsoleTheme, sheet: Boolean = true, content: @Composable () -> Unit) {
        PocketAgentTheme(theme = theme) {
            val t = LocalTokens.current
            Box(Modifier.fillMaxSize().background(t.chrome).padding(top = 8.dp, bottom = 8.dp)) {
                Box(
                    if (sheet) Modifier.fillMaxSize().padding(horizontal = SheetInset).clip(SheetShape)
                        .background(t.bg).border(1.dp, t.border, SheetShape)
                    else Modifier.fillMaxSize(),
                ) { content() }
            }
        }
    }

    // captureToImage Robolectric'te kare bekleyişinde takılır; kök view'ı
    // doğrudan bir bitmap'e çizmek native grafikte güvenilir. Diyalog kendi
    // penceresindedir → WindowManagerGlobal'daki son kök.
    private fun shot(name: String, dialog: Boolean = false) {
        rule.waitForIdle()
        val view = if (dialog) {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getMethod("getInstance").invoke(null)
            val roots = wmg.getDeclaredField("mRoots").apply { isAccessible = true }.get(inst) as List<*>
            roots.last()!!.javaClass.getMethod("getView").invoke(roots.last()) as android.view.View
        } else rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Gerçek kabuk: chrome üst şerit + sheet + alt gezinme.
    @Test fun appShell() {
        val ctl = org.robolectric.Robolectric.buildActivity(dev.pocketagent.android.MainActivity::class.java).setup()
        repeat(5) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(150) }
        val v = ctl.get().window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(out, "app-shell.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun homeDark() {
        val m = manager()
        val r = seeded(db())
        rule.setContent { Shell(PocketConsoleTheme) { HomeScreen(m, r) {} } }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        Thread.sleep(600)
        shot("home-dark")
        m.closeAll()
    }

    @Test fun homeLight() {
        val m = manager()
        val r = seeded(db())
        rule.setContent { Shell(PocketPaperTheme) { HomeScreen(m, r) {} } }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        Thread.sleep(600)
        shot("home-light")
        m.closeAll()
    }

    @Test fun homeEmpty() {
        rule.setContent { Shell(PocketConsoleTheme) { HomeScreen(manager(), ConnectionRepository(db().connections())) {} } }
        shot("home-empty")
    }

    @Test fun connections() {
        val d = db()
        rule.setContent { Shell(PocketConsoleTheme) { ConnectionsScreen(seeded(d), ProfileRepository(d.profiles()), manager()) {} } }
        shot("connections-dark")
    }

    @Test fun connectionsWizard() {
        val d = db()
        rule.setContent { Shell(PocketPaperTheme) { ConnectionsScreen(ConnectionRepository(d.connections()), ProfileRepository(d.profiles()), manager()) {} } }
        shot("connections-wizard-light")
    }

    @Test fun terminal() {
        val m = manager()
        rule.setContent { Shell(PocketConsoleTheme, sheet = false) { TerminalScreen(m, SettingsViewModel(), {}) } }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        m.open(SavedConnection("build-box", "192.168.1.40", 22, "berk", "ram:password", id = "c2"), Secret.Password("x"))
        Thread.sleep(600)
        shot("terminal-dark")
        m.closeAll()
    }

    @Test fun settings() {
        val app = RuntimeEnvironment.getApplication() as dev.pocketagent.android.App
        val hk = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        rule.setContent { Shell(PocketConsoleTheme) { SettingsScreen(SettingsViewModel(), UsageViewModel(), hk, app) } }
        shot("settings-dark")
        rule.onNodeWithText("Görünüm").performClick()
        shot("settings-appearance-dark")
    }

    @Test fun settingsLight() {
        val app = RuntimeEnvironment.getApplication() as dev.pocketagent.android.App
        val hk = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        rule.setContent { Shell(PocketPaperTheme) { SettingsScreen(SettingsViewModel(), UsageViewModel(), hk, app) } }
        shot("settings-light")
    }

    @Test fun filesEmpty() {
        val files = FilesViewModel(manager(), CoroutineScope(Dispatchers.IO), File.createTempFile("cache", "").parentFile!!)
        rule.setContent { Shell(PocketConsoleTheme) { FilesScreen(files) } }
        shot("files-empty-dark")
    }

    @Test fun chat() {
        val blocks = listOf(
            ChatBlockDto("message", "", "Testleri çalıştırıp başarısız olanları düzelteceğim."),
            ChatBlockDto("tool", "", "tool:Bash"),
            ChatBlockDto("result", "", "result:success"),
            ChatBlockDto("tool", "", "tool:Read"),
            ChatBlockDto("tool", "", "tool:Edit"),
            ChatBlockDto("tool", "", "tool:Bash"),
            ChatBlockDto("error", "", "result:exit 1"),
            ChatBlockDto("thinking", "", "Hata bir yarış durumundan kaynaklanıyor olabilir."),
            ChatBlockDto("tool", "", "tool:Write"),
            ChatBlockDto("result", "", "result:success"),
            ChatBlockDto("message", "", "Düzeltildi; 42 test geçiyor."),
        )
        rule.setContent { PocketAgentTheme(PocketConsoleTheme) { ChatDialog("session.jsonl", blocks) {} } }
        shot("chat-dark", dialog = true)
    }
}
