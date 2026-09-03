// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pocketagent.android.App
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
    val settings = remember { SettingsViewModel() }
    val inbox = remember { InboxViewModel() }
    val approval = remember { ApprovalViewModel() }
    val usage = remember { UsageViewModel() }
    val files = remember { FilesViewModel() }
    var tab by remember { mutableStateOf(AppTab.Home) }

    val terminal = app.terminal
    val pendingHostKey by terminal.pendingHostKey.collectAsState()

    LaunchedEffect(Unit) { app.connections.refresh() }

    PocketAgentTheme(dark = settings.theme.dark) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Pocket Agent",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            },
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label, maxLines = 1) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            },
        ) { pad ->
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(pad)
            ) {
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
