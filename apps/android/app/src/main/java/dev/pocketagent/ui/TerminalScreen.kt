// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.TerminalInput
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TerminalViewModel

// P06/P10 terminal ekranı: rozet + scrollback + giriş + boyut düğmeleri.
// Cihanda FakeSshTransport yerine gerçek SshTransport takılır (arayüz aynı).
@Composable
fun TerminalScreen(vm: TerminalViewModel, settings: SettingsViewModel, onSend: (TerminalInput) -> Unit) {
    val frames by vm.frames.collectAsState()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(frames.size) { if (frames.isNotEmpty()) listState.animateScrollToItem(frames.size - 1) }
    val pal = settings.theme.palette
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            AssistChip(onClick = {}, label = { Text("transport: ${vm.badge}") },
                modifier = Modifier.semantics { contentDescription = "Aktif transport ${vm.badge}" })
            Row {
                OutlinedButton(onClick = { vm.shrink(); onSend(TerminalInput.Resize(vm.size)) }) { Text("A-") }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { vm.grow(); onSend(TerminalInput.Resize(vm.size)) }) { Text("A+") }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(Color(pal.background))
                .semantics { contentDescription = "Terminal çıktısı" }
        ) {
            items(frames) { line ->
                Text(
                    line.trimEnd(),
                    color = Color(pal.foreground),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (13 * settings.theme.fontScale).sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }
            if (frames.isEmpty()) item {
                Text(
                    "Bağlanın: profil seç → SSH aç → tmux re-attach (komut tekrarı yok)",
                    color = Color(pal.foreground).copy(alpha = 0.7f),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Komut / metin") },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Terminal girişi" },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { if (text.isNotEmpty()) { onSend(TerminalInput.Text(text + "\n")); text = "" } },
                modifier = Modifier.semantics { contentDescription = "Gönder" }
            ) { Text("Send") }
        }
        Text("pty: ${vm.size.cols}x${vm.size.rows} • Ctrl/Esc/Tab kısayollar Connections'ta",
            style = MaterialTheme.typography.bodySmall)
    }
}

fun defaultTerminalSize(): TerminalSize = TerminalSize(80, 24)
