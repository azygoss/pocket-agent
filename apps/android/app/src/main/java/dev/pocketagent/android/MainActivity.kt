// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.TerminalInput
import dev.pocketagent.transport.TerminalViewModel
import dev.pocketagent.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketAgentApp() }
    }
}

@Composable
fun PocketAgentApp() {
    val settings = remember { SettingsViewModel() }
    val inbox = remember { InboxViewModel() }
    val approval = remember { ApprovalViewModel() }
    val usage = remember { UsageViewModel() }
    val files = remember { FilesViewModel() }
    val shortcuts = remember { ShortcutModel() }
    var tab by remember { mutableStateOf(RouteTab.Home) }
    val th = settings.theme
    MaterialTheme(colorScheme = if (th.dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(bottomBar = {
            Row(Modifier.fillMaxWidth().padding(4.dp)) {
                    RouteTab.entries.forEach { r ->
                        TextButton(onClick = { tab = r }, modifier = Modifier.weight(1f)) {
                            Text(r.label)
                        }
                    }
            }
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
                when (tab) {
                    RouteTab.Home -> HomePane(inbox)
                    RouteTab.Connections -> ConnectionsPane(shortcuts)
                    RouteTab.Sessions -> SessionsPane(settings)
                    RouteTab.Agents -> AgentsPane(inbox, approval)
                    RouteTab.Files -> FilesPane(files)
                    RouteTab.Settings -> SettingsPane(settings, usage)
                }
            }
        }
    }
}

enum class RouteTab(val label: String) {
    Home("Home"),
    Connections("Connections"),
    Sessions("Sessions"),
    Agents("Agents"),
    Files("Files"),
    Settings("Settings")
}

@Composable
fun HomePane(inbox: InboxViewModel) {
    Column {
        Text("Pocket Agent", style = MaterialTheme.typography.headlineMedium)
        ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Başlangıç (60 sn)", style = MaterialTheme.typography.titleMedium)
                Text("1. Host'ta: pocket-agent host setup → QR")
                Text("2. İlk SSH'de parmak izini onayla (pinlenir)")
                Text("3. tmux oturumu seç → resume hazır")
            }
        }
        Text("Unread: ${inbox.rows.count { it.unread }} • sessions: ${inbox.rows.size}")
        Button(onClick = { inbox.add("s1", "e${System.currentTimeMillis()}", "Approve deploy?") }) { Text("Simulate event") }
    }
}

@Composable
fun ConnectionsPane(sc: ShortcutModel) {
    Column {
        Text("Connections", style = MaterialTheme.typography.titleLarge)
        ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Transport: Mosh → ET → SSH (auth/host-key'te fallback yok)")
                Text("tmux prefix: ${sc.tmuxPrefix()} • özel: ${sc.custom.size}")
                Text("Özel port • ProxyJump • Mosh UDP aralığı • ET :2022")
            }
        }
        Button(onClick = { sc.add("Run ${sc.custom.size}", "Ctrl-${sc.custom.size}") }) { Text("Add shortcut") }
    }
}

@Composable
fun SessionsPane(settings: SettingsViewModel) {
    val vm = remember { TerminalViewModel(dev.pocketagent.transport.SessionId("session:demo")) }
    val scope = rememberCoroutineScope()
    val fake = remember { dev.pocketagent.transport.FakeSshTransport() }
    LaunchedEffect(Unit) {
        fake.openPty("xterm-256color", defaultTerminalSize())
        vm.onFrame(fake.read())
    }
    TerminalScreen(vm, settings) { input ->
        scope.launch {
            fake.send(input)
            vm.onFrame(fake.read())
            if (input is TerminalInput.Resize) { /* rozet/boyut vm içinde */ }
        }
    }
}

@Composable
fun AgentsPane(inbox: InboxViewModel, approval: ApprovalViewModel) {
    var filter by remember { mutableStateOf("Tümü") }
    Column {
        Text("Agents", style = MaterialTheme.typography.titleLarge)
        Row {
            listOf("Tümü", "Okunmamış").forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }, modifier = Modifier.padding(end = 4.dp))
            }
        }
        val shown = inbox.rows.filter { if (filter == "Okunmamış") it.unread else true }
        LazyColumn {
            items(shown) { r ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.title, fontSize = 16.sp)
                        Row {
                            Button(onClick = { approval.decide("digest-${r.eventId}", "3", true) }) { Text("Approve") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { inbox.markRead(r.eventId) }) { Text("Read") }
                        }
                        Text("decision: ${approval.lastDecision ?: "-"}")
                    }
                }
            }
        }
        if (inbox.rows.isEmpty()) Text("No active rows — session başına birleşir, 24h TTL")
    }
}

@Composable
fun FilesPane(files: FilesViewModel) {
    Column {
        Text("Files", style = MaterialTheme.typography.titleLarge)
        Text("ws / docs / a.md", style = MaterialTheme.typography.bodySmall)
        Text("canOpen docs/a.md = ${files.canOpen("docs/a.md")}")
        ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("app.apk — binary (listelenir, render edilmez)")
                Text("Paylaşım: 10MB cap • 24h kısa URL • tek-tık revoke")
            }
        }
    }
}

@Composable
fun SettingsPane(s: SettingsViewModel, usage: UsageViewModel) {
    Column {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Row {
            Button(onClick = { s.toggleDark() }) { Text(if (s.theme.dark) "Light" else "Dark") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { s.setFontScale(s.theme.fontScale + 0.1f) }) { Text("A+ ${(s.theme.fontScale)}") }
        }
        usage.rows.forEach { Text("${it.agent}: %${it.percent} (reset ${it.resetIn})") }
        Text("Dikte: cihaz-içi varsayılan • BYOK opt-in • pocketagent://tmux|herdr")
    }
}
