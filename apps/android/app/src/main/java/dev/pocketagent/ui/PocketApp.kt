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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.ui.theme.TermRed
import dev.pocketagent.ui.theme.TerminalFont
import androidx.core.content.ContextCompat
import dev.pocketagent.android.App
import dev.pocketagent.service.TerminalService
import dev.pocketagent.ui.theme.PocketAgentTheme
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
fun PocketAgentApp(app: App, deepLinkAction: MutableStateFlow<String?> = MutableStateFlow(null)) {
    val settings = remember { SettingsViewModel(app.settingsStore, app.appScope) }
    val inbox = app.inbox
    val approval = remember { ApprovalViewModel() }
    val usage = app.usage
    val files = remember { FilesViewModel(app.sessions, app.appScope, app.cacheDir) }
    var tab by remember { mutableStateOf(AppTab.Home) }
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

    PocketAgentTheme(dark = settings.theme.dark, amoled = settings.theme.amoled) {
        Scaffold(
            topBar = { ConsoleTopBar(tab, activeCount) },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { ConsoleNavBar(tab, unread) { tab = it } },
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
                        onConnected = { tab = AppTab.Terminal },
                    )
                    AppTab.Terminal -> TerminalScreen(
                        manager = sessions,
                        settings = settings,
                        onNewConnection = { tab = AppTab.Connections },
                    )
                    AppTab.Agents -> AgentsScreen(inbox = inbox, approval = approval, app = app)
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

// İnce üst bar: prompt glifi + uygulama adı + aktif sekme + oturum sayısı.
@Composable
private fun ConsoleTopBar(tab: AppTab, activeSessions: Int) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "❯",
                    fontFamily = TerminalFont,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "pocket-agent",
                    fontFamily = TerminalFont,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    " / ${tab.label}",
                    fontFamily = TerminalFont,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                if (activeSessions > 0) {
                    StateDot(ConnectionState.ACTIVE)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$activeSessions oturum",
                        fontFamily = TerminalFont,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ConsoleDivider()
        }
    }
}

// Sade alt bar: ikon + kısa mono etiket; aktif sekme üstte ince yeşil çizgi.
@Composable
private fun ConsoleNavBar(current: AppTab, agentUnread: Int, onSelect: (AppTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            ConsoleDivider()
            Row(Modifier.fillMaxWidth().height(54.dp)) {
                AppTab.entries.forEach { t ->
                    val selected = current == t
                    val tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = { onSelect(t) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(2.dp).background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            ),
                        )
                        Spacer(Modifier.height(7.dp))
                        Box {
                            Icon(
                                t.icon,
                                contentDescription = t.label,
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                            if (t == AppTab.Agents && agentUnread > 0) {
                                Text(
                                    "$agentUnread",
                                    fontFamily = TerminalFont,
                                    fontSize = 8.sp,
                                    color = Color(0xFF04150C),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-4).dp)
                                        .background(TermRed, CircleShape)
                                        .padding(horizontal = 3.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            t.short,
                            fontFamily = TerminalFont,
                            fontSize = 9.sp,
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
