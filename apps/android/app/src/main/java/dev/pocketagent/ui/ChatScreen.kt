// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pocketagent.net.ChatBlockDto
import dev.pocketagent.ui.theme.TerminalFont

// P14: agent transcript sohbet görünümü. Bloklar gateway /chat üzerinden gelir
// (tam içerik yalnız SSH tünelinde; backend yalnız ≤256 karakter özet görür).
@Composable
fun ChatDialog(title: String, blocks: List<ChatBlockDto>, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            Modifier.fillMaxWidth(0.94f).padding(vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = TerminalFont,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat")
                    }
                }
                if (blocks.isEmpty()) {
                    Text(
                        "Blok yok — transcript boş ya da tanınmadı.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(blocks) { b -> ChatBlockRow(b) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBlockRow(b: ChatBlockDto) {
    when (b.role) {
        "tool" -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Build, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        b.text.removePrefix("tool:"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = TerminalFont,
                    )
                }
            }
        }
        "result", "error" -> {
            val err = b.role == "error"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (err) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (err) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    b.text.removePrefix("result:"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (err) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            // message: agent balonu (sola yaslı, max %85 genişlik)
            Card(
                Modifier.widthIn(max = 340.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    b.text,
                    Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
