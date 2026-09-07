// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.unit.sp
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.ui.theme.TerminalFont

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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Durum satırı: prompt estetiğinde tek satır özet
        ConsoleCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(state)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (state) {
                        ConnectionState.ACTIVE -> "bağlı — ${activeConn?.user}@${activeConn?.host}"
                        ConnectionState.CONNECTING -> "bağlanıyor…"
                        ConnectionState.RECONNECTING -> "yeniden bağlanıyor…"
                        ConnectionState.FAILED -> "bağlantı hatası"
                        else -> if (sessionList.isEmpty()) "açık oturum yok" else "${sessionList.size} oturum askıda"
                    },
                    fontFamily = TerminalFont,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (state == ConnectionState.ACTIVE && active != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${active.controller.vm.badge} • pty ${active.controller.vm.size.cols}x${active.controller.vm.size.rows} • ${sessionList.size} oturum",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Hızlı eylemler
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onGoTo(AppTab.Connections) }, modifier = Modifier.weight(1f)) {
                Text(
                    if (saved.isEmpty()) "İlk hostu ekle" else "Hostlar (${saved.size})",
                    fontFamily = TerminalFont, fontSize = 12.sp,
                )
            }
            if (state == ConnectionState.ACTIVE) {
                FilledTonalButton(onClick = { onGoTo(AppTab.Terminal) }, modifier = Modifier.weight(1f)) {
                    Text("Terminale git", fontFamily = TerminalFont, fontSize = 12.sp)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            } else if (active?.controller?.canReconnect() == true) {
                FilledTonalButton(
                    onClick = { active.controller.reconnect(); onGoTo(AppTab.Terminal) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Yeniden bağlan", fontFamily = TerminalFont, fontSize = 12.sp)
                }
            }
        }

        // Başlangıç (yalnız hiç host yokken) / Son bağlantılar
        if (saved.isEmpty()) {
            ConsoleCard {
                SectionLabel("Başlangıç")
                Spacer(Modifier.height(8.dp))
                StepRow(1, "Host'ta çalıştır: pocket-agent pair")
                StepRow(2, "QR'ı tara ya da XXXX-XXXX kodunu gir")
                StepRow(3, "İlk bağlantıda parmak izini pinle (TOFU)")
                StepRow(4, "tmux re-attach hazır — kopmaya dayanıklı")
            }
        } else {
            ConsoleCard {
                SectionLabel("Son bağlantılar")
                Spacer(Modifier.height(4.dp))
                saved.sortedByDescending { it.lastConnectedAt }.take(3).forEach { c ->
                    val open = sessionList.firstOrNull { it.conn.id == c.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (open != null || connections.hasSavedSecret(c.id)) {
                                    sessions.open(c, connections.secret(c.id))
                                    onGoTo(AppTab.Terminal)
                                } else {
                                    onGoTo(AppTab.Connections)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StateDot(open?.controller?.state?.collectAsState()?.value ?: ConnectionState.CLOSED)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${c.name} — ${c.user}@${c.host}",
                            fontFamily = TerminalFont, fontSize = 12.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "❯",
                            fontFamily = TerminalFont, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Son agent olayları
        ConsoleCard {
            SectionLabel("Agent olayları")
            Spacer(Modifier.height(6.dp))
            if (inbox.rows.isEmpty()) {
                Text(
                    "Henüz olay yok. Hook'lar kurulunca onay istekleri burada birleşir (24s TTL).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                inbox.rows.take(3).forEach { r ->
                    Text(
                        "• ${r.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
                if (inbox.rows.size > 3) {
                    Text(
                        "+${inbox.rows.size - 3} daha",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepRow(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            "$n.",
            fontFamily = TerminalFont, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
