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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.pocketagent.android.App
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.TermAmber
import dev.pocketagent.ui.theme.TermBlue
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed
import dev.pocketagent.ui.theme.LocalMonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun categoryIcon(cat: String): ImageVector = when (cat) {
    "APPROVAL_REQUIRED" -> Icons.Filled.Warning
    "TASK_COMPLETE" -> Icons.Filled.Check
    "SESSION_STARTED", "SESSION_ENDED" -> Icons.Filled.PlayArrow
    "ERROR" -> Icons.Filled.Warning
    else -> Icons.Filled.Info
}

// RFC3339 → "5 dk önce" (parse edilemezse ham değer).
private fun eventTime(iso: String): String {
    if (iso.isBlank()) return ""
    val inst = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return iso
    return relativeTime(inst.toEpochMilli())
}

@Composable
private fun categoryColor(cat: String) = when (cat) {
    "APPROVAL_REQUIRED" -> TermAmber
    "TASK_COMPLETE" -> TermGreen
    "ERROR" -> TermRed
    else -> TermBlue
}

@Composable
fun AgentsScreen(
    inbox: InboxViewModel,
    approval: ApprovalViewModel,
    app: App,
    backendInfo: String = "",
    onOpenAgent: (InboxRow) -> Unit = {},
) {
    var filter by remember { mutableStateOf("Tümü") }
    val scope = rememberCoroutineScope()
    val syncStatus by app.eventSync.status.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = Space.lg)) {
        Spacer(Modifier.height(Space.lg))
        ScreenHeader("Agentlar")
        Spacer(Modifier.height(Space.md))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentedControl(
                options = listOf(Segment("Tümü", "Tümü"), Segment("Okunmamış", "Okunmamış")),
                selected = filter,
                onSelect = { filter = it },
                modifier = Modifier.width(200.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagPill(
                    syncStatus,
                    active = syncStatus == "bağlı",
                    tone = if (syncStatus == "bağlı") TermGreen else null,
                )
                ConsoleTextButton(
                    onClick = { app.eventSync.syncNow() },
                    enabled = app.backendClient != null,
                ) { Text("Şimdi senkronla") }
            }
        }
        // Teşhis: hangi backend'e poll edildiği görünür olsun — "boş akış"
        // şikayetlerinde ilk kontrol edilecek yer burası.
        Text(
            "backend: ${backendInfo.ifBlank { "ayarlanmadı" }}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalMonoFont.current),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )

        // Aktif agent'lar: sessionId başına son olay SESSION_STARTED olanlar.
        // Dokun → o host'un terminaline düş; tmux oturumuysa attach et.
        val active = activeSessions(inbox.rows)
        if (active.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            SectionLabel("aktif agent'lar")
            Spacer(Modifier.height(Space.sm))
            ConsoleCard(padding = 0.dp) {
                active.forEachIndexed { i, r ->
                    if (i > 0) SoftDivider(Modifier.padding(start = Space.lg))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAgent(r) }
                            .padding(horizontal = Space.lg, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StateDot(dev.pocketagent.transport.ConnectionState.ACTIVE, 8.dp)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(r.source, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOf(r.sessionId, eventTime(r.createdAt))
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalMonoFont.current),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Text(
                            "oturuma git ›",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalMonoFont.current),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.md))
        }

        val shown = inbox.rows.filter { if (filter == "Okunmamış") it.unread else true }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.SmartToy,
                    title = "Aktif agent olayı yok",
                    body = if (syncStatus == "bağlı") {
                        "Host'ta daemon + hook'lar kurulu olmalı: pocket-agent hooks install, " +
                            "ardından daemon'ı başlat (onboard bunu yapar). Olaylar 15s içinde akar."
                    } else {
                        "Ayarlar → Backend ile sunucunu bağla; host'ta da pocket-agent onboard çalıştır."
                    },
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                items(shown, key = { it.eventId }) { r ->
                    val isApproval = r.category == "APPROVAL_REQUIRED" || r.digest.isNotBlank()
                    val tint = categoryColor(r.category)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = Space.md, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(
                                categoryIcon(r.category),
                                tint = tint,
                                containerColor = tint.copy(alpha = 0.14f),
                                size = 34.dp,
                            )
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    listOf(r.source, r.sessionId, eventTime(r.createdAt))
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalMonoFont.current),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            if (r.unread) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                        if (isApproval && r.unread) {
                            Spacer(Modifier.height(Space.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                ConsoleButton(onClick = {
                                    scope.launch(Dispatchers.IO) { decideRemote(app, inbox, approval, r, true) }
                                }) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Onayla")
                                }
                                ConsoleOutlinedButton(onClick = {
                                    scope.launch(Dispatchers.IO) { decideRemote(app, inbox, approval, r, false) }
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reddet")
                                }
                            }
                        } else if (r.unread) {
                            ConsoleTextButton(onClick = { inbox.markRead(r.eventId) }) { Text("Okundu") }
                        }
                    }
                }
            }
        }
        approval.lastError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        approval.lastDecision?.let {
            Text(
                "Son karar: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Onay backend'e gider: 202 = kazandın (kayıt düşer), 409 = başka cihaz kazandı.
private fun decideRemote(app: App, inbox: InboxViewModel, approval: ApprovalViewModel, r: InboxRow, ok: Boolean) {
    val client = app.backendClient
    if (client == null) {
        // backend yoksa yalnız yerel kayıt
        if (approval.decide(r.digest.ifBlank { "local" }, r.revision.ifBlank { "1" }, ok)) inbox.markRead(r.eventId)
        return
    }
    if (r.digest.isBlank() || r.revision.isBlank()) {
        approval.reportError("digest/revision eksik — onay gönderilemez")
        return
    }
    try {
        // not: backend iskeleti decision alanını almıyor; onay/red ayrımı
        // approvalId'ye bağlı — gerçek imza P13 tam diliminde.
        when (client.approvalAction(r.eventId, r.digest, r.revision, app.deviceId)) {
            202 -> {
                approval.decide(r.digest, r.revision, ok)
                inbox.resolve(r.eventId)
            }
            409 -> {
                approval.reportError("Başka bir cihaz bu onayı zaten karşıladı")
                inbox.resolve(r.eventId)
            }
            else -> approval.reportError("Backend hatası: beklenmeyen yanıt")
        }
    } catch (e: Exception) {
        approval.reportError("Backend erişilemedi: ${e.message}")
    }
}
