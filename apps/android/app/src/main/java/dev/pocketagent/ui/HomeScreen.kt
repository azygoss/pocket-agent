// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SessionHandle
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.LocalConsoleTheme
import dev.pocketagent.ui.theme.LocalMonoFont

@Composable
fun HomeScreen(
    sessions: SessionManager,
    connections: ConnectionRepository,
    onGoTo: (AppTab) -> Unit,
) {
    val sessionList by sessions.sessions.collectAsState()
    val activeId by sessions.activeId.collectAsState()
    val customNames by sessions.customNames.collectAsState()
    val saved by connections.items.collectAsState()
    var renaming by remember { mutableStateOf<SessionHandle?>(null) }
    val active = sessionList.firstOrNull { it.id == activeId }
    val state = active?.controller?.state?.collectAsState()?.value ?: ConnectionState.CLOSED
    val primary = MaterialTheme.colorScheme.primary

    // Referans düzen: neredeyse siyah zemin, üstten loş yeşil glow.
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 0.95f,
                    ),
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(Space.lg))

            // ── SESSIONS: canlı terminal önizleme kartları ─────────────────
            if (sessionList.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Oturumlar")
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onGoTo(AppTab.Terminal) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.GridView,
                            contentDescription = "Terminale git",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    items(sessionList, key = { it.id }) { h ->
                        SessionCard(
                            h,
                            name = customNames[h.id],
                            onRename = { renaming = h },
                        ) {
                            sessions.setActive(h.id)
                            onGoTo(AppTab.Terminal)
                        }
                    }
                }
                Spacer(Modifier.height(Space.xl))
            }

            // ── CONNECTIONS: host kartları ─────────────────────────────────
            if (saved.isEmpty()) {
                SectionLabel("Başlangıç")
                Spacer(Modifier.height(Space.md))
                ConsoleCard {
                    StepRow(1, "Host'ta çalıştır: pocket-agent pair")
                    StepRow(2, "QR'ı tara ya da XXXX-XXXX kodunu gir")
                    StepRow(3, "İlk bağlantıda parmak izini pinle (TOFU)")
                    StepRow(4, "tmux re-attach hazır — kopmaya dayanıklı")
                    Spacer(Modifier.height(Space.md))
                    ConsoleButton(onClick = { onGoTo(AppTab.Connections) }) {
                        Text("İlk hostu ekle")
                    }
                }
            } else {
                SectionLabel("Son bağlantılar")
                Spacer(Modifier.height(Space.md))
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    saved.sortedByDescending { it.lastConnectedAt }.take(3).forEach { c ->
                        val open = sessionList.firstOrNull { it.conn.id == c.id }
                        ConnectionTile(
                            name = c.name,
                            address = "${c.user}@${c.host}:${c.port}",
                            state = open?.controller?.state?.collectAsState()?.value ?: ConnectionState.CLOSED,
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
                if (sessionList.isNotEmpty()) {
                    Spacer(Modifier.height(Space.md))
                    TagPill("● ${sessionList.size} oturum", active = true, tone = MaterialTheme.colorScheme.primary)
                }
            }

            // Kopan oturum için hızlı yol.
            if (active != null && state != ConnectionState.ACTIVE && active.controller.canReconnect()) {
                Spacer(Modifier.height(Space.md))
                ConsoleOutlinedButton(
                    onClick = { active.controller.reconnect(); onGoTo(AppTab.Terminal) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Yeniden bağlan") }
            }
            Spacer(Modifier.height(Space.xl))

            // FAB payı.
            Spacer(Modifier.height(96.dp))
        }

        // Büyük dairesel FAB: yeni host → Bağlantılar.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(58.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onGoTo(AppTab.Connections) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Yeni host",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }

        renaming?.let { h ->
            RenameSessionDialog(
                connName = h.conn.name,
                initial = customNames[h.id],
                onDismiss = { renaming = null },
                onSave = { name -> sessions.rename(h.id, name); renaming = null },
            )
        }
    }
}

// Oturum kartı: gerçek buffer'ın son satırlarıyla mini terminal önizlemesi.
// Placeholder yok — kart, oturumun canlı çıktısını gösterir.
// Uzun basma → yeniden adlandırma diyaloğu (ad SessionManager.customNames'te).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    h: SessionHandle,
    name: String?,
    onRename: () -> Unit,
    onClick: () -> Unit,
) {
    val console = LocalConsoleTheme.current
    val lines by h.controller.vm.lines.collectAsState()
    val st by h.controller.state.collectAsState()
    Column(Modifier.width(216.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(console.term.background))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onRename)
                .padding(start = 10.dp, end = 10.dp, top = 9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(st, size = 6.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    name ?: h.conn.name,
                    fontFamily = LocalMonoFont.current,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color(console.accentAlt).copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        h.controller.vm.badge,
                        fontFamily = LocalMonoFont.current,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(console.accentAlt),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column {
                lines.takeLast(7).forEach { l ->
                    Text(
                        l.toAnnotatedString(ansi = console.term.ansi),
                        color = Color(console.term.foreground),
                        fontFamily = LocalMonoFont.current,
                        fontSize = 7.5.sp,
                        lineHeight = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${h.conn.user}@${h.conn.host}",
            fontFamily = LocalMonoFont.current,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

// Bağlantı kartı: köşesinde durum noktalı ikon karosu + iki satır + chevron.
@Composable
private fun ConnectionTile(
    name: String,
    address: String,
    state: ConnectionState,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconTile(
                Icons.Filled.Dns,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 42.dp,
            )
            Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp)) {
                StateDot(state, size = 9.dp)
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                address,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalMonoFont.current),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
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
