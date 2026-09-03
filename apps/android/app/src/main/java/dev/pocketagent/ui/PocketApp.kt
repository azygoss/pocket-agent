// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import dev.pocketagent.android.App
import dev.pocketagent.service.TerminalService
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.ui.theme.PocketAgentTheme

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
fun PocketAgentApp(app: App) {
    val settings = remember { SettingsViewModel(app.settingsStore, app.appScope) }
    val inbox = remember { InboxViewModel() }
    val approval = remember { ApprovalViewModel() }
    val usage = remember { UsageViewModel() }
    val files = remember { FilesViewModel() }
    var tab by remember { mutableStateOf(AppTab.Home) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val terminal = app.terminal
    val pendingHostKey by terminal.pendingHostKey.collectAsState()
    val connState by terminal.state.collectAsState()
    val unread = inbox.rows.count { it.unread }

    LaunchedEffect(Unit) { app.connections.refresh() }

    // Bağlantı durumu değişimlerinde bildirim + foreground service.
    LaunchedEffect(Unit) {
        var prev = ConnectionState.CLOSED
        snapshotFlow { terminal.state.value }.collect { s ->
            if (s == ConnectionState.ACTIVE && prev != ConnectionState.ACTIVE) {
                snackbar.showSnackbar("Bağlandı")
                ContextCompat.startForegroundService(context, Intent(context, TerminalService::class.java))
            }
            if ((s == ConnectionState.CLOSED || s == ConnectionState.FAILED) && prev == ConnectionState.ACTIVE) {
                context.stopService(Intent(context, TerminalService::class.java))
                if (s == ConnectionState.CLOSED) snackbar.showSnackbar("Bağlantı kapandı")
            }
            prev = s
        }
    }

    PocketAgentTheme(dark = settings.theme.dark) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "pocket-agent",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
                        terminal = terminal,
                        connections = app.connections,
                        inbox = inbox,
                        onGoTo = { tab = it },
                    )
                    AppTab.Connections -> ConnectionsScreen(
                        repo = app.connections,
                        terminal = terminal,
                        onConnected = { tab = AppTab.Terminal },
                    )
                    AppTab.Terminal -> TerminalScreen(controller = terminal, settings = settings)
                    AppTab.Agents -> AgentsScreen(inbox = inbox, approval = approval)
                    AppTab.Files -> FilesScreen(files = files)
                    AppTab.Settings -> SettingsScreen(settings = settings, usage = usage)
                }
            }
        }
    }

    // TOFU: ilk bağlantıda parmak izi onayı; değişim zaten hard-stop.
    pendingHostKey?.let { key ->
        AlertDialog(
            onDismissRequest = { terminal.rejectHostKey() },
            title = { Text("Host anahtarını onayla") },
            text = {
                Text(
                    "${key.host}:${key.port} için sunulan anahtar ilk kez görülüyor.\n\n" +
                        "Algoritma: ${key.algorithm}\nParmak izi:\n${key.fingerprint}\n\n" +
                        "Doğruladıysan pinle; anahtar daha sonra değişirse bağlantı durur.",
                )
            },
            confirmButton = {
                Button(onClick = { terminal.acceptHostKeyAndReconnect() }) { Text("Pinle ve bağlan") }
            },
            dismissButton = {
                TextButton(onClick = { terminal.rejectHostKey() }) { Text("Vazgeç") }
            },
        )
    }
}
