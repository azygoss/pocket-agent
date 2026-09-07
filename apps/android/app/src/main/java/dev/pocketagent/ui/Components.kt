// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.ui.theme.TermAmber
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed

// ── Console bileşenleri: tüm ekranların ortak görsel dili ──────────────────

// Düz, ince-border'lı kart (M3'ün yuvarlak tonal kartı yerine).
@Composable
fun ConsoleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

// Bölüm başlığı: mono, dim, hafif letterspacing ("BAŞLIK" değil, "Başlık").
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun StateDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.ACTIVE -> TermGreen
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING, ConnectionState.SUSPENDED -> TermAmber
        ConnectionState.FAILED -> TermRed
        ConnectionState.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

// İnce ayırıcı çizgi (border rengi).
@Composable
fun ConsoleDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

// "az önce / 5 dk önce / 3 sa önce / 2 g önce" — bağlantı kartlarında son
// bağlanma zamanı için.
fun relativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    if (ts <= 0) return "hiç"
    val d = (now - ts) / 1000
    return when {
        d < 60 -> "az önce"
        d < 3600 -> "${d / 60} dk önce"
        d < 86400 -> "${d / 3600} sa önce"
        else -> "${d / 86400} g önce"
    }
}
