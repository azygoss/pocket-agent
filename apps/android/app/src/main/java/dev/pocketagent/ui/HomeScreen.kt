// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SessionManager

@Composable
fun HomeScreen(
    sessions: SessionManager,
    connections: ConnectionRepository,
    inbox: InboxViewModel,
    onGoTo: (AppTab) -> Unit,
) {
    val sessionList by sessions.sessions.collectAsState()
    val activeId by sessions.activeId.collectAsState()
    val saved by connections.items.collectAsState()
    val active = sessionList.firstOrNull { it.id == activeId }
    val state = active?.controller?.state?.collectAsState()?.value ?: ConnectionState.CLOSED
    val activeConn = active?.conn

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Bağlantı durumu
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bağlantı", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StateDot(state)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (state) {
                            ConnectionState.ACTIVE -> "Bağlı — ${activeConn?.user}@${activeConn?.host}"
                            ConnectionState.CONNECTING -> "Bağlanıyor…"
                            ConnectionState.RECONNECTING -> "Yeniden bağlanıyor…"
                            ConnectionState.FAILED -> "Bağlantı hatası"
                            else -> if (sessionList.isEmpty()) "Açık oturum yok" else "${sessionList.size} oturum askıda"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (state == ConnectionState.ACTIVE && active != null) {
                    Text(
                        "Transport: ${active.controller.vm.badge} • pty ${active.controller.vm.size.cols}x${active.controller.vm.size.rows} • ${sessionList.size} oturum",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Hızlı eylemler
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onGoTo(AppTab.Connections) }, modifier = Modifier.weight(1f)) {
                Text(if (saved.isEmpty()) "İlk hostu ekle" else "Hostlar (${saved.size})")
            }
            if (state == ConnectionState.ACTIVE) {
                FilledTonalButton(onClick = { onGoTo(AppTab.Terminal) }, modifier = Modifier.weight(1f)) {
                    Text("Terminale git")
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                }
            } else if (active?.controller?.canReconnect() == true) {
                FilledTonalButton(onClick = { active.controller.reconnect(); onGoTo(AppTab.Terminal) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Yeniden bağlan")
                }
            }
        }

        // Başlangıç
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Başlangıç", style = MaterialTheme.typography.titleMedium)
                StepRow(1, "Host'ta çalıştır: pocket-agent host setup")
                StepRow(2, "Bağlantılar sekmesinden hostu ekle")
                StepRow(3, "İlk bağlantıda parmak izini pinle (TOFU)")
                StepRow(4, "Terminalde oturum aç — tmux re-attach hazır")
            }
        }

        // Son agent olayları
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Agent olayları", style = MaterialTheme.typography.titleMedium)
                }
                if (inbox.rows.isEmpty()) {
                    Text(
                        "Henüz olay yok. Hook'lar kurulunca onay istekleri burada birleşir (24s TTL).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    inbox.rows.take(3).forEach { r ->
                        Text("• ${r.title}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (inbox.rows.size > 3) {
                        Text("+${inbox.rows.size - 3} daha", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepRow(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                "$n",
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
