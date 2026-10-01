// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SessionHandle
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.ui.theme.consoleTheme
import dev.pocketagent.ui.theme.consoleFont
import dev.pocketagent.ui.theme.LocalMonoFont
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import dev.pocketagent.android.App
import dev.pocketagent.service.TerminalService
import dev.pocketagent.ui.theme.BlinkClock
import dev.pocketagent.ui.theme.BlinkClockDriver
import dev.pocketagent.ui.theme.PocketAgentTheme
import dev.pocketagent.ui.theme.Readout
import dev.pocketagent.ui.theme.SheetInset
import dev.pocketagent.ui.theme.SheetShape
import dev.pocketagent.ui.theme.Space
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// Seçili sekme dolu ikon, diğerleri çizgi ikon — aynı aile, tek hiyerarşi.
enum class AppTab(val label: String, val icon: ImageVector, val iconIdle: ImageVector) {
    Home("Ana Sayfa", Icons.Filled.Home, Icons.Outlined.Home),
    Connections("Bağlantılar", Icons.Filled.Dns, Icons.Outlined.Dns),
    Terminal("Terminal", Icons.Filled.Terminal, Icons.Outlined.Terminal),
    Files("Dosyalar", Icons.Filled.Folder, Icons.Outlined.Folder),
    Settings("Ayarlar", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketAgentApp(
    app: App,
    deepLinkAction: MutableStateFlow<String?> = MutableStateFlow(null),
    addHostLink: MutableStateFlow<dev.pocketagent.transport.SavedConnection?> = MutableStateFlow(null),
) {
    val settings = remember { SettingsViewModel(app.settingsStore, app.appScope) }
    val usage = app.usage
    val files = remember { FilesViewModel(app.sessions, app.appScope, app.cacheDir) }
    var tab by remember { mutableStateOf(AppTab.Home) }
    // Terminal chrome: arama/tam-ekran bayrakları üst barla paylaşılır.
    val termChrome = remember { TerminalChromeState() }
    val termFullscreen = termChrome.fullscreen
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    val sessions = app.sessions
    val connections2 = app.connections.items.collectAsState()
    val hostKeyPrompt by sessions.hostKeyPrompt.collectAsState()
    val anyActive by sessions.anyActive.collectAsState()

    LaunchedEffect(Unit) { app.connections.refresh(); app.profiles.refresh() }

    // Açılışta otomatik reconnect (opt-in): yalnız Keystore'da secret saklıysa.
    var autoReconnectTried by remember { mutableStateOf(false) }
    // Kopmada yeniden bağlanma tercihini oturum yöneticisine yansıt.
    LaunchedEffect(settings.autoReconnectOnDrop) { sessions.autoReconnectOnDrop = settings.autoReconnectOnDrop }
    LaunchedEffect(settings.autoReconnect, connections2.value) {
        if (!settings.autoReconnect || autoReconnectTried) return@LaunchedEffect
        val conns = connections2.value
        if (conns.isEmpty() || sessions.sessions.value.isNotEmpty()) return@LaunchedEffect
        autoReconnectTried = true
        val last = conns.maxByOrNull { it.lastConnectedAt } ?: return@LaunchedEffect
        val secret = app.connections.secret(last.id) ?: return@LaunchedEffect // RAM/Keystore'da yoksa elle bağlan
        sessions.open(last, secret)
        tab = AppTab.Terminal
    }

    // Oturum persistansı: açık oturumların conn id'leri diske yazılır;
    // açılışta kullanıcı kapatmadıysa geri yüklenip yeniden bağlanır
    // (uzak tarafta tmux re-attach kaldığı yerden devam ettirir).
    var sessionsRestored by remember { mutableStateOf(false) }
    LaunchedEffect(connections2.value) {
        if (sessionsRestored) return@LaunchedEffect
        val conns = connections2.value
        if (conns.isEmpty()) return@LaunchedEffect
        sessionsRestored = true
        app.settingsStore.loadOpenSessions().forEach { os ->
            conns.firstOrNull { it.id == os.connId }?.let { c ->
                app.connections.secret(c.id)?.let { s ->
                    // Aynı host'ta birden çok kayıtlı oturum → paralel aç;
                    // kayıtlı tmux adı aynı uzak oturuma reattach eder.
                    sessions.open(
                        c, s,
                        forceNew = sessions.sessions.value.any { it.conn.id == c.id },
                        customName = os.name,
                        tmuxName = os.tmux,
                    )
                }
            }
        }
    }
    // Liste yalnız restore denendikten sonra diske yazılır — aksi halde ilk
    // boş emission kayıtlı id'leri okunmadan silerdi. Özel adlar da aynı
    // kayda gömülür; ad değişimi liste değişmeden tetikler (combine).
    LaunchedEffect(sessionsRestored) {
        if (!sessionsRestored) return@LaunchedEffect
        kotlinx.coroutines.flow.combine(
            sessions.sessions,
            sessions.customNames,
            sessions.tmuxNames,
        ) { list, names, tmuxes ->
            list.map {
                dev.pocketagent.data.OpenSession(it.conn.id, names[it.id], tmuxes[it.id])
            }
        }.collect { app.settingsStore.saveOpenSessions(it) }
    }

    // pocketagent://tmux|herdr → Terminal sekmesine düş.
    LaunchedEffect(Unit) {
        deepLinkAction.collect { action ->
            if (action != null) {
                tab = AppTab.Terminal
                deepLinkAction.value = null
            }
        }
    }

    // Sekme değişince tam ekran bayrağını sıfırla (nav bar geri gelir).
    LaunchedEffect(tab) { if (tab != AppTab.Terminal) { termChrome.fullscreen = false; termChrome.searchOpen = false } }

    // pocketagent://add?host=… → Bağlantılar sekmesi (diyalog ekranda açılır).
    LaunchedEffect(Unit) {
        addHostLink.collect { c -> if (c != null) tab = AppTab.Connections }
    }

    // Backend ayarı değişince client'ı yeniden kur, poller'ı başlat.
    LaunchedEffect(settings.backendUrl, settings.tenantToken) {
        app.configureBackend(settings.backendUrl, settings.tenantToken)
        if (settings.backendConfigured) app.eventSync.start() else app.eventSync.stop()
    }

    // Oturum varken foreground service ayakta; yokken durur.
    val sessionList by sessions.sessions.collectAsState()
    val activeCount = sessionList.count { it.controller.state.collectAsState().value == dev.pocketagent.transport.ConnectionState.ACTIVE }
    LaunchedEffect(activeCount) {
        if (activeCount > 0) {
            // Android 13+: bildirim izni yoksa FGS bildirimi görünmez — iste.
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            ContextCompat.startForegroundService(context, Intent(context, TerminalService::class.java))
            TerminalService.updateCount(context, activeCount)
        } else if (!anyActive) {
            context.stopService(Intent(context, TerminalService::class.java))
        }
    }
    LaunchedEffect(anyActive) {
        if (anyActive) snackbar.showSnackbar("Oturum bağlandı")
    }

    // Terminalde geri tuşu da çıkış yoludur: arama/tam-ekran açıksa onlar
    // önce kapanır (TerminalScreen'in kendi BackHandler'ı önceliklidir),
    // değilse oturumlar bölümüne (ana sayfa) dönülür — oturum yaşar.
    androidx.activity.compose.BackHandler(
        enabled = tab == AppTab.Terminal && sessionList.isNotEmpty(),
    ) { tab = AppTab.Home }

    val console = consoleTheme(settings.theme.themeId)
    // Sistem çubuğu ikon kontrastı temayı takip eder — açık temada koyu ikon.
    val barView = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(console.dark) {
        val w = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(w, barView).apply {
            isAppearanceLightStatusBars = !console.dark
            isAppearanceLightNavigationBars = !console.dark
        }
    }
    // Ortak 1 Hz saat: "çalışıyor" pikselleri tek kaynaktan yanıp söner.
    val blink = remember { BlinkClock() }
    BlinkClockDriver(blink)
    PocketAgentTheme(
        theme = console,
        mono = consoleFont(settings.theme.fontId).family,
        blink = blink,
    ) {
        val t = Tok
        val immersive = tab == AppTab.Terminal && termFullscreen
        Scaffold(
            containerColor = if (immersive) Color(console.term.background) else t.chrome,
            topBar = {
                // Terminal tam ekrandayken üst bar da gizlenir — gerçek immersive.
                if (!immersive) {
                    ConsoleTopBar(activeCount) {
                        // Terminal sekmesindeyken aksiyonlar üst barda yaşar.
                        if (tab == AppTab.Terminal) {
                            val activeId by sessions.activeId.collectAsState()
                            sessionList.firstOrNull { it.id == activeId }?.let { h ->
                                TerminalBarActions(h, sessions, termChrome, onExit = { tab = AppTab.Home })
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            // Bar'lar kendi inset'lerini uygular; fullscreen'de (bar yokken)
            // status/nav boşluğu içerikte kalmasın diye sıfırlanır.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // Aktif terminalde ana nav bar tamamen gizlenir — çıkış
                // tutamaç çekişi, üst bardaki X veya geri tuşuyla yapılır.
                // Oturum yokken (boş terminal) bar görünür kalır ki ekran
                // çıkışsız bir tuzağa dönüşmesin.
                if (tab != AppTab.Terminal || sessionList.isEmpty()) {
                    ConsoleNavBar(tab) { tab = it }
                }
            },
        ) { pad ->
            // Sheets on chrome: ana içerik chrome üstünde 8dp içeride duran
            // bir sheet'tir. Terminal kendi sheet'ini (panel) taşır — şerit ve
            // tuş kapsülü doğrudan chrome üstünde durur.
            val sheet = tab != AppTab.Terminal || sessionList.isEmpty()
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .then(
                        if (sheet) {
                            Modifier
                                .padding(horizontal = SheetInset)
                                .clip(SheetShape)
                                .background(t.bg)
                                .border(1.dp, t.border, SheetShape)
                        } else Modifier,
                    ),
            ) {
                when (tab) {
                    AppTab.Home -> HomeScreen(
                        sessions = sessions,
                        connections = app.connections,
                        onGoTo = { tab = it },
                    )
                    AppTab.Connections -> ConnectionsScreen(
                        repo = app.connections,
                        profiles = app.profiles,
                        sessions = sessions,
                        app = app,
                        settings = settings,
                        pendingAdd = addHostLink,
                        onConnected = { tab = AppTab.Terminal },
                    )
                    AppTab.Terminal -> TerminalScreen(
                        manager = sessions,
                        settings = settings,
                        onNewConnection = { tab = AppTab.Connections },
                        onCollapse = { tab = AppTab.Home },
                        chrome = termChrome,
                    )
                    AppTab.Files -> FilesScreen(files = files, onOpenTerminal = { tab = AppTab.Terminal })
                    AppTab.Settings -> SettingsScreen(
                        settings = settings,
                        usage = usage,
                        hostKeys = app.hostKeys,
                        app = app,
                    )
                }
            }
        }

        // TOFU: ilk bağlantıda parmak izi onayı; değişim zaten hard-stop.
        // Amber = sana ihtiyaç var.
        hostKeyPrompt?.let { prompt ->
            val key = prompt.key
            AlertDialog(
                onDismissRequest = { prompt.controller.rejectHostKey() },
                icon = { SignalPixel(Signal.NeedsYou, size = 12.dp) },
                title = { Text("Host anahtarını onayla") },
                text = {
                    Column {
                        Text(
                            "${key.host}:${key.port} için sunulan anahtar ilk kez görülüyor. " +
                                "Doğruladıysan pinle; anahtar daha sonra değişirse bağlantı durur.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(Space.md))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(t.bg)
                                .border(1.dp, t.border, MaterialTheme.shapes.small)
                                .padding(Space.md),
                        ) {
                            Text(key.algorithm, style = MaterialTheme.typography.labelMedium.copy(fontFamily = Readout), color = t.muted)
                            Spacer(Modifier.height(4.dp))
                            Text(key.fingerprint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout), color = t.text)
                        }
                    }
                },
                confirmButton = {
                    ConsoleButton(onClick = { prompt.controller.acceptHostKeyAndReconnect() }) { Text("Pinle ve bağlan") }
                },
                dismissButton = {
                    TextButton(onClick = { prompt.controller.rejectHostKey() }) { Text("Vazgeç", color = t.text2) }
                },
            )
        }
    }
}

// ── Uygulama chrome'u ───────────────────────────────────────────────────────
// Pencere chrome'dur: üst şerit ve alt gezinme doğrudan chrome üstünde
// durur; içerik onların arasında bir sheet'tir. Seçili sekme yalnız
// zeminiyle (active) işaretlenir — mavi sinyale saklıdır.

// Piksel işareti: "›" istemi kare piksellerden + iki sinyal pikseli.
@Composable
fun PocketMark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val t = Tok
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val cell = this.size.width / 5f
        val gap = cell * 0.18f
        fun px(c: Int, r: Int, color: Color) = drawRoundRect(
            color,
            topLeft = androidx.compose.ui.geometry.Offset(c * cell + gap / 2, r * cell + gap / 2),
            size = androidx.compose.ui.geometry.Size(cell - gap, cell - gap),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.12f),
        )
        listOf(0 to 0, 1 to 1, 2 to 2, 1 to 3, 0 to 4).forEach { (c, r) -> px(c, r, t.accent) }
        px(3, 4, t.warning)
        px(4, 4, t.danger)
    }
}

// Üst şerit: piksel işareti + wordmark, sağda canlı oturum okuması ve
// isteğe bağlı aksiyonlar (Terminal sekmesinde ara/paylaş/tam-ekran/kapat).
@Composable
private fun ConsoleTopBar(activeSessions: Int, actions: (@Composable () -> Unit)? = null) {
    val t = Tok
    Row(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = Space.lg, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PocketMark(size = 16.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            "pocket-agent",
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = Readout),
            color = t.text,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (activeSessions > 0) {
            Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalPixel(Signal.Live, size = 7.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "$activeSessions oturum",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
                    color = t.text2,
                )
            }
        }
        actions?.invoke()
    }
}

// Terminal aksiyonları üst barda: ara / paylaş / tam ekran / yeniden bağlan /
// kapat. X oturumu kapatır ve terminalden çıkar (oturumlar bölümüne dönülür).
@Composable
private fun TerminalBarActions(h: SessionHandle, sessions: SessionManager, chrome: TerminalChromeState, onExit: () -> Unit) {
    val state by h.controller.state.collectAsState()
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TerminalBarIcon("Scrollback'te ara", Icons.Filled.Search, active = chrome.searchOpen) { chrome.searchOpen = !chrome.searchOpen }
        TerminalBarIcon("Scrollback'i paylaş", Icons.Filled.Share) {
            shareScrollback(context, h.controller.vm.lines.value)
        }
        TerminalBarIcon("Tam ekran", Icons.Filled.Fullscreen) { chrome.fullscreen = true }
        if ((state == ConnectionState.CLOSED || state == ConnectionState.FAILED) && h.controller.canReconnect()) {
            TerminalBarIcon("Yeniden bağlan", Icons.Filled.Refresh, tint = Tok.accent) {
                h.controller.reconnect()
            }
        }
        TerminalBarIcon("Oturumu kapat", Icons.Filled.Close, tint = Tok.danger) {
            sessions.close(h.id)
            onExit()
        }
    }
}

// 40dp görsel, 48dp dokunma hedefi; açık durumdaki araç zeminle işaretlenir.
@Composable
private fun TerminalBarIcon(
    desc: String,
    icon: ImageVector,
    tint: Color = Tok.text2,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (active) Tok.active else Color.Transparent)
            .semantics { contentDescription = desc }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// Alt gezinme: chrome üstünde; seçili sekme ikon+etiketin arkasındaki
// active zeminiyle — renk değişmez, mürekkep kalır.
@Composable
private fun ConsoleNavBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    val t = Tok
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .padding(horizontal = SheetInset, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppTab.entries.forEach { tab ->
            val selected = current == tab
            val bg by animateColorAsState(if (selected) t.active else Color.Transparent, tween(130), label = "nav-bg")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.medium)
                    .background(bg)
                    .semantics { contentDescription = tab.label }
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (selected) tab.icon else tab.iconIdle,
                    contentDescription = null,
                    tint = if (selected) t.text else t.muted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) t.text else t.muted,
                    maxLines = 1,
                )
            }
        }
    }
}
