// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.ui.theme.TermRed
import dev.pocketagent.ui.theme.LocalMonoFont
import dev.pocketagent.ui.theme.consoleTheme
import dev.pocketagent.ui.theme.consoleFont
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import dev.pocketagent.android.App
import dev.pocketagent.service.TerminalService
import dev.pocketagent.ui.theme.PocketAgentTheme
import dev.pocketagent.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow

enum class AppTab(val label: String, val short: String, val icon: ImageVector) {
    Home("Ana Sayfa", "ana", Icons.Filled.Home),
    Connections("Bağlantılar", "host", Icons.Filled.Dns),
    Terminal("Terminal", "term", Icons.Filled.Terminal),
    Agents("Agentlar", "agent", Icons.Filled.SmartToy),
    Files("Dosyalar", "dosya", Icons.Filled.Folder),
    Settings("Ayarlar", "ayar", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketAgentApp(
    app: App,
    deepLinkAction: MutableStateFlow<String?> = MutableStateFlow(null),
    addHostLink: MutableStateFlow<dev.pocketagent.transport.SavedConnection?> = MutableStateFlow(null),
) {
    val settings = remember { SettingsViewModel(app.settingsStore, app.appScope) }
    val inbox = app.inbox
    val approval = remember { ApprovalViewModel() }
    val usage = app.usage
    val files = remember { FilesViewModel(app.sessions, app.appScope, app.cacheDir) }
    var tab by remember { mutableStateOf(AppTab.Home) }
    // Terminal tam ekran: nav bar gizlenir, yerine terminal tuş şeridi gelir.
    var termFullscreen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    val sessions = app.sessions
    val connections2 = app.connections.items.collectAsState()
    val hostKeyPrompt by sessions.hostKeyPrompt.collectAsState()
    val anyActive by sessions.anyActive.collectAsState()
    val unread = inbox.rows.count { it.unread }

    LaunchedEffect(Unit) { app.connections.refresh() }

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
    LaunchedEffect(tab) { if (tab != AppTab.Terminal) termFullscreen = false }

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
                    ConsoleTopBar(tab, activeCount)
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            // Bar'lar kendi inset'lerini uygular; fullscreen'de (bar yokken)
            // status/nav boşluğu içerikte kalmasın diye sıfırlanır.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // Terminal tam ekrandayken ana menü barı yerine terminalin
                // kendi tuş şeridi görünür (TerminalScreen içinde render edilir).
                if (!(tab == AppTab.Terminal && termFullscreen)) {
                    ConsoleNavBar(tab, unread) { tab = it }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    AppTab.Home -> HomeScreen(
                        sessions = sessions,
                        connections = app.connections,
                        inbox = inbox,
                        onGoTo = { tab = it },
                    )
                    AppTab.Connections -> ConnectionsScreen(
                        repo = app.connections,
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
                        onFullscreenChange = { termFullscreen = it },
                    )
                    AppTab.Agents -> AgentsScreen(
                        inbox = inbox, approval = approval, app = app,
                        backendInfo = "${settings.backendUrl.ifBlank { "ayarlanmadı" }} · tenant: ${settings.tenantToken.ifBlank { "—" }}",
                    )
                    AppTab.Files -> FilesScreen(files = files)
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

// ── Console chrome ──────────────────────────────────────────────────────────

// İnce üst bar: marka glifi + ekran başlığı + sağda oturum durumu.
// Edge-to-edge'de status bar altına kaymaması için kendi inset'ini uygular.
@Composable
private fun ConsoleTopBar(tab: AppTab, activeSessions: Int) {
    val mono = LocalMonoFont.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "❯",
                        fontFamily = mono,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(Space.md))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                if (activeSessions > 0) {
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StateDot(ConnectionState.ACTIVE, size = 6.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$activeSessions oturum",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            ConsoleDivider()
        }
    }
}

// Alt bar: ikon + mono etiket. Aktif sekme yuvarlatılmış vurgu kutusunda
// (üst çizgi yerine dolgu — daha modern, daha net vurgu).
@Composable
private fun ConsoleNavBar(current: AppTab, agentUnread: Int, onSelect: (AppTab) -> Unit) {
    val mono = LocalMonoFont.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column {
            ConsoleDivider()
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp)) {
                AppTab.entries.forEach { t ->
                    val selected = current == t
                    val tint by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "nav-tint",
                    )
                    val pill by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                        label = "nav-pill",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(t) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(pill)
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box {
                                Icon(
                                    t.icon,
                                    contentDescription = t.label,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp),
                                )
                                if (t == AppTab.Agents && agentUnread > 0) {
                                    Text(
                                        "$agentUnread",
                                        fontFamily = mono,
                                        fontSize = 8.sp,
                                        color = Color(0xFF04160D),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-4).dp)
                                            .background(TermRed, CircleShape)
                                            .padding(horizontal = 3.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(t.short, fontFamily = mono, fontSize = 10.sp, color = tint, maxLines = 1)
                    }
                }
            }
        }
    }
}
