// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg),
    ) {
        Spacer(Modifier.height(Space.lg))
        ScreenHeader("Ana sayfa")
        Spacer(Modifier.height(Space.xl))

        // ── Durum: tek bakışta bağlantı okuması ────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateDot(state, size = 10.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(stateText(state, sessionList.size), style = MaterialTheme.typography.titleLarge)
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
            Spacer(Modifier.height(Space.sm))
            Text(
                "pty ${active.controller.vm.size.cols}×${active.controller.vm.size.rows} · ${sessionList.size} oturum",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalMonoFont.current),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp),
            )
        }
        Spacer(Modifier.height(Space.lg))

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
        Spacer(Modifier.height(Space.xxl))
        SoftDivider()
        Spacer(Modifier.height(Space.lg))

        // ── Başlangıç / Son bağlantılar ────────────────────────────────────
        if (saved.isEmpty()) {
            SectionLabel("Başlangıç")
            Spacer(Modifier.height(Space.md))
            StepRow(1, "Host'ta çalıştır: pocket-agent pair")
            StepRow(2, "QR'ı tara ya da XXXX-XXXX kodunu gir")
            StepRow(3, "İlk bağlantıda parmak izini pinle (TOFU)")
            StepRow(4, "tmux re-attach hazır — kopmaya dayanıklı")
        } else {
            SectionLabel("Son bağlantılar")
            Spacer(Modifier.height(Space.xs))
            saved.sortedByDescending { it.lastConnectedAt }.take(3).forEachIndexed { i, c ->
                val open = sessionList.firstOrNull { it.conn.id == c.id }
                if (i > 0) SoftDivider()
                ListRow(
                    title = "${c.name} — ${c.user}@${c.host}",
                    titleMono = true,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp),
                    leading = {
                        StateDot(open?.controller?.state?.collectAsState()?.value ?: ConnectionState.CLOSED)
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
        Spacer(Modifier.height(Space.xl))
        SoftDivider()
        Spacer(Modifier.height(Space.lg))

        // ── Son agent olayları ─────────────────────────────────────────────
        SectionLabel("Agent olayları")
        Spacer(Modifier.height(Space.sm))
        if (inbox.rows.isEmpty()) {
            Text(
                "Henüz olay yok. Hook'lar kurulunca onay istekleri burada birleşir (24s TTL).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            inbox.rows.take(3).forEach { r ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    StateDot(ConnectionState.ACTIVE, size = 5.dp)
                    Spacer(Modifier.width(Space.md))
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
        Spacer(Modifier.height(Space.xxl))
    }
}

private fun stateText(state: ConnectionState, sessionCount: Int): String = when (state) {
    ConnectionState.ACTIVE -> "Bağlı"
    ConnectionState.CONNECTING -> "Bağlanıyor…"
    ConnectionState.RECONNECTING -> "Yeniden bağlanıyor…"
    ConnectionState.FAILED -> "Bağlantı hatası"
    else -> if (sessionCount == 0) "Açık oturum yok" else "$sessionCount oturum askıda"
}

// Numaralı adım: mono sıra numarası + metin.
@Composable
private fun StepRow(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            "%02d".format(n),
            fontFamily = LocalMonoFont.current,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(26.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
