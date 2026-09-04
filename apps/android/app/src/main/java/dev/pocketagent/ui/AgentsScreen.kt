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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pocketagent.android.App
import dev.pocketagent.ui.theme.TermAmber
import dev.pocketagent.ui.theme.TermBlue
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed
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
fun AgentsScreen(inbox: InboxViewModel, approval: ApprovalViewModel, app: App) {
    var filter by remember { mutableStateOf("Tümü") }
    val scope = rememberCoroutineScope()
    val syncStatus by app.eventSync.status.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Tümü", "Okunmamış").forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "sync: $syncStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.TextButton(
                    onClick = { app.eventSync.syncNow() },
                    enabled = app.backendClient != null,
                ) { Text("Şimdi senkronla", style = MaterialTheme.typography.bodySmall) }
            }
        }

        val shown = inbox.rows.filter { if (filter == "Okunmamış") it.unread else true }
        if (shown.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("Aktif agent olayı yok", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (syncStatus == "bağlı") {
                        "Hook'lar olay ürettikçe burada birleşir; 24 saat sonra düşer."
                    } else {
                        "Ayarlar → Backend ile sunucunu bağla; hook olayları 15s içinde akar."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.eventId }) { r ->
                    val isApproval = r.category == "APPROVAL_REQUIRED" || r.digest.isNotBlank()
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (r.unread) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    categoryIcon(r.category),
                                    contentDescription = null,
                                    tint = categoryColor(r.category),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        listOf(r.source, r.sessionId, eventTime(r.createdAt)).filter { it.isNotBlank() }
                                            .joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (isApproval && r.unread) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            decideRemote(app, inbox, approval, r, true)
                                        }
                                    }) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Onayla")
                                    }
                                    OutlinedButton(onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            decideRemote(app, inbox, approval, r, false)
                                        }
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reddet")
                                    }
                                }
                            } else if (r.unread) {
                                TextButtonRow { inbox.markRead(r.eventId) }
                            }
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

@Composable
private fun TextButtonRow(onRead: () -> Unit) {
    OutlinedButton(onClick = onRead) { Text("Okundu") }
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
