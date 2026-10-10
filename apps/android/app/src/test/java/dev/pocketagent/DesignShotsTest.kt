// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.data.ProfileRepository
import dev.pocketagent.net.ChatBlockDto
import dev.pocketagent.transport.FakeSshTransport
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.SavedProfile
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.ChatDialog
import dev.pocketagent.ui.FilesScreen
import dev.pocketagent.ui.FilesViewModel
import dev.pocketagent.ui.HostEditor
import dev.pocketagent.ui.HubScreen
import dev.pocketagent.ui.PairingSheet
import dev.pocketagent.ui.SettingsScreen
import dev.pocketagent.ui.SettingsViewModel
import dev.pocketagent.ui.TerminalScreen
import dev.pocketagent.ui.UsageViewModel
import dev.pocketagent.ui.theme.ConsoleTheme
import dev.pocketagent.ui.theme.LocalTokens
import dev.pocketagent.ui.theme.PocketAgentTheme
import dev.pocketagent.ui.theme.PocketConsoleTheme
import dev.pocketagent.ui.theme.PocketPaperTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    // Gerçekçi terminal çıktısı: yankılanan açılış komutlarını ekran
    // temizliğiyle siler, sonra bir test koşusu basar.
    private class Demo(val text: String = DEMO) : SshConnector {
        override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
            FakeSshTransport().also { t ->
                t.openPty("xterm-256color", size)
                CoroutineScope(Dispatchers.IO).launch { delay(250); t.emitOutput("\u001B[2J\u001B[H$text") }
            }
    }

    private class ListingTransport(val inner: FakeSshTransport = FakeSshTransport()) :
        SshTransport by inner, dev.pocketagent.transport.SftpSession {
        override suspend fun home() = "/home/berk/src/pocket-agent"
        override suspend fun list(path: String): List<dev.pocketagent.transport.RemoteFile> {
            val now = System.currentTimeMillis() / 1000
            fun d(n: String, age: Long) = dev.pocketagent.transport.RemoteFile(n, "$path/$n", true, 0, now - age)
            fun f(n: String, size: Long, age: Long) = dev.pocketagent.transport.RemoteFile(n, "$path/$n", false, size, now - age)
            return listOf(
                d(".git", 60), d(".github", 86400), f(".env", 120, 600),
                d("apps", 3600), d("backend", 86400), d("docs", 600), d("host", 7200),
                f("README.md", 9_412, 300), f("Makefile", 2_210, 90_000), f("go.mod", 1_024, 400_000),
                f("icon.png", 182_400, 5_000), f("release.tar.gz", 48_000_000, 20_000), f("main.go", 4_800, 120),
            )
        }
        override suspend fun readBytes(path: String, maxBytes: Long): ByteArray = when {
            path.endsWith(".png") -> {
                // Gradyan + çerçeve: görsel okuyucu çekimi için.
                val b = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(b)
                c.drawColor(0xFF1B1F23.toInt())
                val p = android.graphics.Paint().apply { color = 0xFF3DDC97.toInt() }
                c.drawRoundRect(android.graphics.RectF(160f, 80f, 480f, 320f), 48f, 48f, p)
                java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            }
            else -> MAIN_GO.toByteArray()
        }
        override suspend fun writeBytes(path: String, data: ByteArray) {}
        override suspend fun mkdir(path: String) {}
        override suspend fun delete(path: String, isDir: Boolean) {}
        override suspend fun rename(from: String, to: String) {}
    }

    private fun manager(listing: Boolean = false, text: String = DEMO) = SessionManager(
        CoroutineScope(Dispatchers.IO),
        TofuHostKeyStore(File.createTempFile("hostkeys", ".db")),
    ) {
        if (!listing) Demo(text) else object : SshConnector {
            override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
                ListingTransport().also { it.openPty("xterm-256color", size) }
        }
    }

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

    private fun profiles(db: AppDatabase): ProfileRepository {
        val p = ProfileRepository(db.profiles())
        runBlocking {
            p.upsert(SavedProfile("Claude", "claude", connectionId = "c1"))
            p.upsert(SavedProfile("Codex", "codex", connectionId = "c2"))
            p.upsert(SavedProfile("htop", "htop"))
        }
        return p
    }

    @Composable
    private fun Shell(theme: ConsoleTheme, content: @Composable () -> Unit) {
        PocketAgentTheme(theme = theme) {
            Box(Modifier.fillMaxSize().background(LocalTokens.current.bg)) { content() }
        }
    }

    // captureToImage Robolectric'te kare bekleyişinde takılır; kök view'ı
    // doğrudan bir bitmap'e çizmek native grafikte güvenilir. Diyalog kendi
    // penceresindedir → WindowManagerGlobal'daki son kök.
    private fun shot(name: String, dialog: Boolean = false) {
        // Thread.sleep Compose saatini ilerletmez: debounce'lu boyut hesabı ve
        // animasyonlar çekimden önce tamamlansın.
        rule.mainClock.advanceTimeBy(600)
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

    private fun inbox(): dev.pocketagent.ui.InboxViewModel = dev.pocketagent.ui.InboxViewModel().apply {
        val now = java.time.Instant.now()
        // Olay 1 ve 3, demo oturumunun tmux'unda çalışan agent'tan gelir.
        fun ev(id: String, cat: String, src: String, msg: String, ago: Long) = dev.pocketagent.net.BackendEvent(
            id, "h:1", if (id == "1" || id == "3") "tmux:pa-demo0001" else "s-$id", src, cat, msg, "d$id", "r$id",
            now.minusSeconds(ago).toString(), now.plusSeconds(86400).toString(),
        )
        // Backend olayları zaman sırasıyla gelir: aynı oturumdan yenisi eskiyi
        // (okunmamışsa) geçersiz kılar — onay, çalışan aracı izler.
        mergeRemote(listOf(
            ev("4", "ERROR", "codex", "lint başarısız: 3 uyarı hata olarak işaretlendi", 3600),
            ev("2", "TASK_COMPLETE", "codex", "Testler geçti, PR açıklaması hazır", 600),
            ev("3", "TOOL_RUNNING", "claude", "go test ./... çalışıyor", 90),
            ev("1", "APPROVAL_REQUIRED", "claude", "Bash: rm -rf build/ çalıştırmak için onay bekliyor", 40),
        ))
        markRead("4")
    }

    private fun hub(theme: ConsoleTheme, name: String, withInbox: Boolean = false, scroll: String? = null) {
        val m = manager()
        val d = db()
        val r = seeded(d)
        val p = profiles(d)
        val ib = if (withInbox) inbox() else null
        rule.setContent {
            Shell(theme) { HubScreen(m, r, p, inbox = ib, syncStatus = if (withInbox) "bağlı" else null, onOpenSession = {}) }
        }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"), forceNew = true, tmuxName = "pa-demo0001")
        Thread.sleep(900)
        if (scroll != null) rule.onNode(androidx.compose.ui.test.hasScrollToKeyAction()).performScrollToKey(scroll)
        shot(name)
        m.closeAll()
    }

    @Test fun hubDark() = hub(PocketConsoleTheme, "hub-dark", withInbox = true)

    @Test fun hubDarkScrolled() = hub(PocketConsoleTheme, "hub-dark-scrolled", withInbox = true, scroll = "more")

    @Test fun hubLight() = hub(PocketPaperTheme, "hub-light", withInbox = true)

    @Test fun hubFirstRun() {
        val d = db()
        rule.setContent { Shell(PocketConsoleTheme) { HubScreen(manager(), ConnectionRepository(d.connections()), ProfileRepository(d.profiles()), onOpenSession = {}) } }
        shot("hub-first-run-dark")
    }

    @Test fun hubFirstRunLight() {
        val d = db()
        rule.setContent { Shell(PocketPaperTheme) { HubScreen(manager(), ConnectionRepository(d.connections()), ProfileRepository(d.profiles()), onOpenSession = {}) } }
        shot("hub-first-run-light")
    }

    @Test fun workspace() {
        val m = manager()
        rule.setContent { Shell(PocketConsoleTheme) { TerminalScreen(m, SettingsViewModel(), {}) } }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        m.open(SavedConnection("build-box", "192.168.1.40", 22, "berk", "ram:password", id = "c2"), Secret.Password("x"))
        Thread.sleep(900)
        shot("workspace-dark")
        m.closeAll()
    }

    @Test fun workspaceLight() {
        val m = manager()
        rule.setContent { Shell(PocketPaperTheme) { TerminalScreen(m, SettingsViewModel(), {}) } }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        Thread.sleep(900)
        shot("workspace-light")
        m.closeAll()
    }

    @Test fun workspaceMenu() {
        val m = manager()
        rule.setContent { Shell(PocketConsoleTheme) { TerminalScreen(m, SettingsViewModel(), {}) } }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        Thread.sleep(900)
        rule.onNodeWithContentDescription("Diğer").performClick()
        shot("workspace-menu-dark", dialog = true)
        m.closeAll()
    }

    @Test fun closeSheet() {
        val m = manager()
        rule.setContent { Shell(PocketConsoleTheme) { TerminalScreen(m, SettingsViewModel(), {}) } }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        Thread.sleep(900)
        rule.onNodeWithContentDescription("Diğer").performClick()
        rule.onNodeWithText("Oturumu kapat…").performClick()
        Thread.sleep(400)
        shot("sheet-close-dark", dialog = true)
        m.closeAll()
    }

    @Test fun workspaceEmpty() {
        rule.setContent { Shell(PocketConsoleTheme) { TerminalScreen(manager(), SettingsViewModel(), {}) } }
        shot("workspace-empty-dark")
    }

    @Test fun filesListing() {
        val m = manager(listing = true)
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        Thread.sleep(500)
        val files = FilesViewModel(m, CoroutineScope(Dispatchers.IO), File.createTempFile("cache", "").parentFile!!)
        rule.setContent { Shell(PocketConsoleTheme) { FilesScreen(files, onBack = {}) } }
        rule.waitForIdle(); Thread.sleep(500)
        shot("files-list-dark")
        m.closeAll()
    }

    @Test fun filesEmpty() {
        val files = FilesViewModel(manager(), CoroutineScope(Dispatchers.IO), File.createTempFile("cache", "").parentFile!!)
        rule.setContent { Shell(PocketConsoleTheme) { FilesScreen(files, onBack = {}) } }
        shot("files-empty-dark")
    }

    @Test fun settings() {
        val app = RuntimeEnvironment.getApplication() as dev.pocketagent.android.App
        val hk = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        rule.setContent { Shell(PocketConsoleTheme) { SettingsScreen(SettingsViewModel(), UsageViewModel(), hk, app, onBack = {}) } }
        shot("settings-dark")
        rule.onNodeWithText("Görünüm").performClick()
        shot("settings-appearance-dark")
    }

    @Test fun settingsLight() {
        val app = RuntimeEnvironment.getApplication() as dev.pocketagent.android.App
        val hk = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        rule.setContent { Shell(PocketPaperTheme) { SettingsScreen(SettingsViewModel(), UsageViewModel(), hk, app, onBack = {}) } }
        shot("settings-light")
    }

    @Test fun settingsPages() {
        val app = RuntimeEnvironment.getApplication() as dev.pocketagent.android.App
        val hk = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        val usage = UsageViewModel().apply {
            updateFrom(org.json.JSONArray("""[{"agent":"claude","percent":62,"reset_in":"2 sa"},{"agent":"codex","percent":91,"reset_in":"40 dk"}]"""))
        }
        val vm = SettingsViewModel().apply { updateSnippets("gs=git status\nht=htop") }
        rule.setContent { Shell(PocketConsoleTheme) { SettingsScreen(vm, usage, hk, app) } }
        for ((label, file) in listOf("Terminal ve oturum" to "session", "Backend" to "backend", "Yedekleme ve kullanım" to "data", "Hakkında" to "about")) {
            rule.onNodeWithText(label).performScrollTo().performClick()
            shot("settings-$file-dark")
            rule.onNode(hasContentDescription("Geri")).performClick()
            rule.waitForIdle()
        }
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

    @Test fun hostEditor() {
        rule.setContent {
            Shell(PocketConsoleTheme) {
                HostEditor(SavedConnection("", "", 22, "", "ram:password"), onDismiss = {}, onSave = { _, _, _ -> })
            }
        }
        shot("editor-host-dark", dialog = true)
    }

    @Test fun pairing() {
        rule.setContent {
            Shell(PocketConsoleTheme) {
                PairingSheet(backendUrl = "", busy = false, error = null, onDismiss = {}, onScan = {}, onCode = { _, _ -> })
            }
        }
        Thread.sleep(400)
        shot("sheet-pair-dark", dialog = true)
    }

    // Launcher ikonu: uyarlanabilir katmanlar yuvarlak-kare ve daire maskede.
    // 108dp katmanın görünen 72dp'si ikon boyutuna oturur → 1.5x ölçek.
    @Test fun launcherIcon() {
        rule.setContent {
            Row(
                Modifier.fillMaxSize().background(Color(0xFF2B2F33)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(RoundedCornerShape(30), CircleShape).forEach { shape ->
                    Box(Modifier.size(120.dp).clip(shape).background(colorResource(dev.pocketagent.android.R.color.ic_launcher_background))) {
                        Image(
                            painterResource(dev.pocketagent.android.R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().scale(1.5f),
                        )
                    }
                }
            }
        }
        shot("launcher-icon")
    }

    @Test fun textViewerFocusedLine() {
        val m = manager(listing = true)
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        Thread.sleep(500)
        val files = FilesViewModel(m, CoroutineScope(Dispatchers.IO), File.createTempFile("cache", "").parentFile!!)
        rule.setContent { Shell(PocketConsoleTheme) { FilesScreen(files, onBack = {}) } }
        rule.waitForIdle(); Thread.sleep(400)
        files.revealPath("/home/berk/src/pocket-agent/main.go", 14)
        Thread.sleep(600)
        shot("viewer-text-dark", dialog = true)
        m.closeAll()
    }

    @Test fun imageViewer() {
        val m = manager(listing = true)
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        Thread.sleep(500)
        val files = FilesViewModel(m, CoroutineScope(Dispatchers.IO), File.createTempFile("cache", "").parentFile!!)
        rule.setContent { Shell(PocketConsoleTheme) { FilesScreen(files, onBack = {}) } }
        rule.waitForIdle(); Thread.sleep(400)
        files.onFile(dev.pocketagent.transport.RemoteFile("icon.png", "/home/berk/src/pocket-agent/icon.png", false, 182_400, 0))
        Thread.sleep(800)
        shot("viewer-image-dark", dialog = true)
        m.closeAll()
    }

    // Yatay telefon: oturumlar ve terminal; büyük yazı: sistem ölçeği 1.5.
    @Test @Config(sdk = [34], qualifiers = "w852dp-h393dp-land-xhdpi")
    fun hubLandscape() = hub(PocketConsoleTheme, "hub-landscape-dark", withInbox = true)

    @Test @Config(sdk = [34], qualifiers = "w852dp-h393dp-land-xhdpi")
    fun workspaceLandscape() {
        val m = manager()
        rule.setContent { Shell(PocketConsoleTheme) { TerminalScreen(m, SettingsViewModel(), {}) } }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        Thread.sleep(900)
        shot("workspace-landscape-dark")
        m.closeAll()
    }

    @Test fun hubLargeFont() {
        RuntimeEnvironment.setFontScale(1.5f)
        hub(PocketConsoleTheme, "hub-font150-dark", withInbox = true)
    }

    @Test fun workspaceLargeFont() {
        RuntimeEnvironment.setFontScale(1.5f)
        val m = manager()
        rule.setContent { Shell(PocketConsoleTheme) { TerminalScreen(m, SettingsViewModel(), {}) } }
        m.open(SavedConnection("prod-web", "10.0.0.5", 22, "root", "ram:password", id = "c1"), Secret.Password("x"))
        m.open(SavedConnection("build-box", "192.168.1.40", 22, "berk", "ram:password", id = "c2"), Secret.Password("x"))
        Thread.sleep(900)
        shot("workspace-font150-dark")
        m.closeAll()
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
    fun hubSmallPhone() = hub(PocketPaperTheme, "hub-small-light", withInbox = true)

    companion object {
        val MAIN_GO = (1..40).joinToString("\n") { i ->
            when (i) {
                1 -> "package main"
                3 -> "import \"fmt\""
                5 -> "func main() {"
                14 -> "    if err := store.Wipe(user); err != nil { // TODO: rows remain"
                40 -> "}"
                else -> "    fmt.Println(\"step $i\")"
            }
        }
        const val DEMO =
            "\u001B[38;5;114m~/src/pocket-agent\u001B[0m \u001B[1m❯\u001B[0m go test ./...\r\n" +
                "ok   dev/pocketagent/host/gateway   0.41s\r\n" +
                "ok   dev/pocketagent/host/session   0.12s\r\n" +
                "ok   dev/pocketagent/host/tmux      0.08s\r\n" +
                "\u001B[31m--- FAIL: TestWipeUser (0.02s)\u001B[0m\r\n" +
                "    store_test.go:88: want 0 rows, got 1\r\n" +
                "\u001B[31mFAIL\u001B[0m dev/pocketagent/backend/store  1.03s\r\n" +
                "\r\n" +
                "\u001B[38;5;114m~/src/pocket-agent\u001B[0m \u001B[1m❯\u001B[0m "
    }
}
