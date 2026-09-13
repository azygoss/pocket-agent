// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pocketagent.net.ChatBlockDto
import dev.pocketagent.ui.theme.LocalMonoFont
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed

// P14: agent transcript sohbet görünümü. Bloklar gateway /chat üzerinden gelir
// (tam içerik yalnız SSH tünelinde; backend yalnız ≤256 karakter özet görür).
// Sunum: transcript hissi — sol kenarda rol işaretli satırlar, balon yok.
@Composable
fun ChatDialog(title: String, blocks: List<ChatBlockDto>, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            Modifier.fillMaxWidth(0.94f).padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "❯",
                        fontFamily = LocalMonoFont.current,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = LocalMonoFont.current,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat")
                    }
                }
                ConsoleDivider()
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

// Rol işareti: sol kenarda 2dp renk şeridi + mono rol etiketi.
@Composable
private fun ChatBlockRow(b: ChatBlockDto) {
    val (marker, tint) = when (b.role) {
        "tool" -> "tool" to MaterialTheme.colorScheme.onSurfaceVariant
        "result" -> "ok" to TermGreen
        "error" -> "err" to TermRed
        else -> "msg" to MaterialTheme.colorScheme.primary
    }
    Row {
        Box(
            Modifier
                .width(2.dp)
                .height(34.dp)
                .align(Alignment.CenterVertically)
                .background(tint.copy(alpha = 0.7f)),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                marker,
                fontFamily = LocalMonoFont.current,
                fontSize = 9.sp,
                color = tint,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            Text(
                when (b.role) {
                    "tool" -> b.text.removePrefix("tool:")
                    "result", "error" -> b.text.removePrefix("result:")
                    else -> b.text
                },
                style = if (b.role == "tool" || b.role == "result" || b.role == "error")
                    MaterialTheme.typography.bodySmall.copy(fontFamily = LocalMonoFont.current)
                else MaterialTheme.typography.bodyMedium,
                color = when (b.role) {
                    "error" -> MaterialTheme.colorScheme.error
                    "tool", "result" -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
