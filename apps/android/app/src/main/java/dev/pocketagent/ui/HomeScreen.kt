// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
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
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.LocalMonoFont

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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        // ── Durum hero'u: bir bakışta bağlantı durumu ──────────────────────
        ConsoleCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(state, size = 10.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(stateText(state, sessionList.size), style = MaterialTheme.typography.titleMedium)
                    if (state == ConnectionState.ACTIVE && activeConn != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${activeConn.user}@${activeConn.host}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalMonoFont.current),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (state == ConnectionState.ACTIVE && active != null) {
                    TagPill(active.controller.vm.badge, active = true, tone = MaterialTheme.colorScheme.primary)
                }
            }
            if (state == ConnectionState.ACTIVE && active != null) {
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                    Metric("pty", "${active.controller.vm.size.cols}×${active.controller.vm.size.rows}")
                    Metric("oturum", "${sessionList.size}")
                }
            }
        }

        // ── Hızlı eylemler ─────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            ConsoleButton(onClick = { onGoTo(AppTab.Connections) }, modifier = Modifier.weight(1f)) {
                Text(if (saved.isEmpty()) "İlk hostu ekle" else "Hostlar (${saved.size})")
            }
            if (state == ConnectionState.ACTIVE) {
                ConsoleOutlinedButton(onClick = { onGoTo(AppTab.Terminal) }, modifier = Modifier.weight(1f)) {
                    Text("Terminal")
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            } else if (active?.controller?.canReconnect() == true) {
                ConsoleOutlinedButton(
                    onClick = { active.controller.reconnect(); onGoTo(AppTab.Terminal) },
                    modifier = Modifier.weight(1f),
                ) { Text("Yeniden bağlan") }
            }
        }

        // ── Başlangıç / Son bağlantılar ────────────────────────────────────
        if (saved.isEmpty()) {
            ConsoleCard {
                CardHeader("Başlangıç")
                Spacer(Modifier.height(Space.md))
                StepRow(1, "Host'ta çalıştır: pocket-agent pair")
                StepRow(2, "QR'ı tara ya da XXXX-XXXX kodunu gir")
                StepRow(3, "İlk bağlantıda parmak izini pinle (TOFU)")
                StepRow(4, "tmux re-attach hazır — kopmaya dayanıklı")
            }
        } else {
            ConsoleCard(padding = Space.sm) {
                Box(Modifier.padding(start = Space.lg, top = Space.sm, end = Space.lg)) {
                    CardHeader("Son bağlantılar")
                }
                Spacer(Modifier.height(Space.xs))
                saved.sortedByDescending { it.lastConnectedAt }.take(3).forEach { c ->
                    val open = sessionList.firstOrNull { it.conn.id == c.id }
                    ListRow(
                        title = "${c.name} — ${c.user}@${c.host}",
                        titleMono = true,
                        leading = {
                            StateDot(open?.controller?.state?.collectAsState()?.value ?: ConnectionState.CLOSED)
                        },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            if (open != null || connections.hasSavedSecret(c.id)) {
                                sessions.open(c, connections.secret(c.id))
                                onGoTo(AppTab.Terminal)
                            } else {
                                onGoTo(AppTab.Connections)
                            }
                        },
                    )
                }
            }
        }

        // ── Son agent olayları ─────────────────────────────────────────────
        ConsoleCard {
            CardHeader("Agent olayları")
            Spacer(Modifier.height(Space.sm))
            if (inbox.rows.isEmpty()) {
                Text(
                    "Henüz olay yok. Hook'lar kurulunca onay istekleri burada birleşir (24s TTL).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                inbox.rows.take(3).forEach { r ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(5.dp)
                                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
                if (inbox.rows.size > 3) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "+${inbox.rows.size - 3} daha",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
    }
}

private fun stateText(state: ConnectionState, sessionCount: Int): String = when (state) {
    ConnectionState.ACTIVE -> "Bağlı"
    ConnectionState.CONNECTING -> "Bağlanıyor…"
    ConnectionState.RECONNECTING -> "Yeniden bağlanıyor…"
    ConnectionState.FAILED -> "Bağlantı hatası"
    else -> if (sessionCount == 0) "Açık oturum yok" else "$sessionCount oturum askıda"
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun StepRow(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            "$n",
            fontFamily = LocalMonoFont.current,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
