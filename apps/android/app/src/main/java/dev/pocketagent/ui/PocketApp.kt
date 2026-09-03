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
import androidx.core.content.ContextCompat
import dev.pocketagent.android.App
import dev.pocketagent.service.TerminalService
import dev.pocketagent.ui.theme.PocketAgentTheme
import kotlinx.coroutines.flow.MutableStateFlow

enum class AppTab(val label: String, val icon: ImageVector) {
    Home("Ana Sayfa", Icons.Filled.Home),
    Connections("Bağlantılar", Icons.Filled.Dns),
    Terminal("Terminal", Icons.Filled.Terminal),
    Agents("Agentlar", Icons.Filled.SmartToy),
    Files("Dosyalar", Icons.Filled.Folder),
    Settings("Ayarlar", Icons.Filled.Settings),
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

    val sessions = app.sessions
    val hostKeyPrompt by sessions.hostKeyPrompt.collectAsState()
    val anyActive by sessions.anyActive.collectAsState()
    val unread = inbox.rows.count { it.unread }

    LaunchedEffect(Unit) { app.connections.refresh() }

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
    LaunchedEffect(anyActive) {
        if (anyActive) {
            ContextCompat.startForegroundService(context, Intent(context, TerminalService::class.java))
            snackbar.showSnackbar("Oturum bağlandı")
        } else {
            context.stopService(Intent(context, TerminalService::class.java))
        }
    }

    PocketAgentTheme(dark = settings.theme.dark, amoled = settings.theme.amoled) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "pocket-agent",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(tab.label, style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                if (t == AppTab.Agents && unread > 0) {
                                    BadgedBox(badge = { Badge { Text("$unread") } }) {
                                        Icon(t.icon, contentDescription = t.label)
                                    }
                                } else {
                                    Icon(t.icon, contentDescription = t.label)
                                }
                            },
                            label = { Text(t.label, maxLines = 1) },
                            alwaysShowLabel = false,
                        )
                    }
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
