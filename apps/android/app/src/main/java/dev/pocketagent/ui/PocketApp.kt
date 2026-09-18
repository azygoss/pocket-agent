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
import dev.pocketagent.ui.theme.PocketAgentTheme
import dev.pocketagent.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class AppTab(val label: String, val icon: ImageVector) {
    Home("Ana Sayfa", Icons.Filled.Home),
    Connections("Bağlantılar", Icons.Filled.Dns),
    Terminal("Terminal", Icons.Filled.Terminal),
    Files("Dosyalar", Icons.Filled.Folder),
    Settings("Ayarlar", Icons.Filled.Settings),
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
    PocketAgentTheme(
        theme = console,
        mono = consoleFont(settings.theme.fontId).family,
    ) {
        Scaffold(
            topBar = {
                // Terminal tam ekrandayken üst bar da gizlenir — gerçek immersive.
                if (!(tab == AppTab.Terminal && termFullscreen)) {
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
            Box(Modifier.fillMaxSize().padding(pad)) {
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
    }

    // TOFU: ilk bağlantıda parmak izi onayı; değişim zaten hard-stop.
    hostKeyPrompt?.let { prompt ->
        val key = prompt.key
        AlertDialog(
            onDismissRequest = { prompt.controller.rejectHostKey() },
            title = { Text("Host anahtarını onayla") },
            text = {
                Text(
                    "${key.host}:${key.port} için sunulan anahtar ilk kez görülüyor.\n\n" +
                        "Algoritma: ${key.algorithm}\nParmak izi:\n${key.fingerprint}\n\n" +
                        "Doğruladıysan pinle; anahtar daha sonra değişirse bağlantı durur.",
                )
            },
            confirmButton = {
                Button(onClick = { prompt.controller.acceptHostKeyAndReconnect() }) { Text("Pinle ve bağlan") }
            },
            dismissButton = {
                TextButton(onClick = { prompt.controller.rejectHostKey() }) { Text("Vazgeç") }
            },
        )
    }
}

// ── Uygulama chrome'u — tonal dil ────────────────────────────────────────────
// Üst bar ince bir şerit: wordmark + canlı oturum pill'i. Alt bar M3 usulü:
// aktif sekme ikonunun arkasında yatay accent pill'i, etiket altta.

// Üst bar: wordmark + sağda tonal oturum pill'i + isteğe bağlı aksiyonlar
// (Terminal sekmesindeyken ara/paylaş/tam-ekran/kapat burada yaşar).
// Edge-to-edge'de status bar altına kaymaması için kendi inset'ini uygular.
@Composable
private fun ConsoleTopBar(activeSessions: Int, actions: (@Composable () -> Unit)? = null) {
    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(start = Space.lg, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "pocket-agent",
                fontFamily = LocalMonoFont.current,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (activeSessions > 0) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StateDot(ConnectionState.ACTIVE, size = 6.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$activeSessions oturum",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalMonoFont.current),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            actions?.invoke()
        }
        ConsoleDivider()
    }
}

// Terminal aksiyonları üst barda: ara / paylaş / tam ekran / yeniden bağlan /
// kapat. X oturumu kapatır ve terminalden çıkar (oturumlar bölümüne dönülür).
@Composable
private fun TerminalBarActions(h: SessionHandle, sessions: SessionManager, chrome: TerminalChromeState, onExit: () -> Unit) {
    val state by h.controller.state.collectAsState()
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TerminalBarIcon("Scrollback'te ara", Icons.Filled.Search) { chrome.searchOpen = !chrome.searchOpen }
        TerminalBarIcon("Scrollback'i paylaş", Icons.Filled.Share) {
            shareScrollback(context, h.controller.vm.lines.value)
        }
        TerminalBarIcon("Tam ekran", Icons.Filled.Fullscreen) { chrome.fullscreen = true }
        if ((state == ConnectionState.CLOSED || state == ConnectionState.FAILED) && h.controller.canReconnect()) {
            TerminalBarIcon("Yeniden bağlan", Icons.Filled.Refresh, tint = MaterialTheme.colorScheme.primary) {
                h.controller.reconnect()
            }
        }
        TerminalBarIcon("Oturumu kapat", Icons.Filled.Close, tint = MaterialTheme.colorScheme.error) {
            sessions.close(h.id)
            onExit()
        }
    }
}

@Composable
private fun TerminalBarIcon(desc: String, icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 2.dp)
            .size(34.dp)
            .clip(RoundedCornerShape(50))
            .semantics { contentDescription = desc }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// Alt bar: aktif sekmenin ikonu yatay accent pill'i içinde (M3 indicator),
// etiket altta accent renkte.
@Composable
private fun ConsoleNavBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        ConsoleDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp),
        ) {
            AppTab.entries.forEach { t ->
                val selected = current == t
                val tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = t.label }
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(t) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else Color.Transparent,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            t.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label,
                        fontSize = 9.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
