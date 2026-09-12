// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.shortHash
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    // Agents → "oturuma git": opaque host'u kayıtlı bağlantıya eşle (emit.go
    // h:sha256(hostID)[0:16] ile aynı kural), oturumu aç/odakla, tmux
    // oturumuysa attach et.
    val openAgent: (InboxRow) -> Unit = { row ->
        tab = AppTab.Terminal
        app.appScope.launch {
            val conns = connections2.value
            val conn = conns.firstOrNull { c ->
                listOf(c.host, "host:${c.host}", "${c.user}@${c.host}", c.name, "host:${c.name}")
                    .any { "h:" + shortHash(it) == row.host }
            } ?: conns.maxByOrNull { it.lastConnectedAt }
            if (conn == null) {
                snackbar.showSnackbar("Agent host'u için kayıtlı bağlantı yok")
                return@launch
            }
            val ctl = sessions.open(conn, app.connections.secret(conn.id))
            if (row.sessionId.startsWith("tmux:")) {
                val name = row.sessionId.removePrefix("tmux:")
                kotlinx.coroutines.withTimeoutOrNull(15_000) {
                    ctl.state.first { it == dev.pocketagent.transport.ConnectionState.ACTIVE }
                } ?: return@launch
                ctl.send(dev.pocketagent.transport.TerminalInput.Text("tmux attach -t $name\r"))
            }
        }
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
                        onFullscreenChange = { termFullscreen = it },
                    )
                    AppTab.Agents -> AgentsScreen(
                        inbox = inbox, approval = approval, app = app,
                        backendInfo = "${settings.backendUrl.ifBlank { "ayarlanmadı" }} · tenant: ${settings.tenantToken.ifBlank { "—" }}",
                        onOpenAgent = openAgent,
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

// ── Uygulama chrome'u — "status-line" dili ───────────────────────────────────
// Üst bar bir kabuk prompt'u, alt bar bir tmux window-list'tir. Seçim
// her yerde "reverse video" ile gösterilir: accent zemin + onPrimary metin.

// Üst bar: `❯ pocket-agent  ~/sekme` prompt'u + sağda canlı oturum sayacı.
// Edge-to-edge'de status bar altına kaymaması için kendi inset'ini uygular.
@Composable
private fun ConsoleTopBar(tab: AppTab, activeSessions: Int) {
    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "❯",
                fontFamily = LocalMonoFont.current,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "pocket-agent",
                fontFamily = LocalMonoFont.current,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                "  ~/${tab.short}",
                fontFamily = LocalMonoFont.current,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (activeSessions > 0) {
                Row(
                    Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.extraSmall,
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StateDot(ConnectionState.ACTIVE, size = 6.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$activeSessions sess",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalMonoFont.current),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ConsoleDivider()
    }
}

// Alt bar: tmux status-line. Her sekme `i:ad` hücresi; aktif pencere `*`
// ile reverse-video blokta, Agents'ta okunmamış `#n` bayrağıyla durur.
@Composable
private fun ConsoleNavBar(current: AppTab, agentUnread: Int, onSelect: (AppTab) -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        ConsoleDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(50.dp),
        ) {
            AppTab.entries.forEachIndexed { i, t ->
                val selected = current == t
                val label = buildString {
                    append(i)
                    append(':')
                    append(t.short)
                    if (selected) append('*')
                    if (t == AppTab.Agents && agentUnread > 0) append('#').append(agentUnread)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = t.label }
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(t) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontFamily = LocalMonoFont.current,
                        fontSize = 10.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (selected) {
                            Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        } else Modifier,
                    )
                }
            }
        }
    }
}
