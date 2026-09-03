// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pocketagent.ui.theme.TermBg
import dev.pocketagent.ui.theme.TermBlue
import dev.pocketagent.ui.theme.TermGreen

@Composable
fun SettingsScreen(settings: SettingsViewModel, usage: UsageViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Görünüm
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Görünüm", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Koyu tema", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Terminal paleti koyu temayla eşleşir",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.theme.dark, onCheckedChange = { settings.toggleDark() })
                }
                Text("Yazı ölçeği: ${"%.1f".format(settings.theme.fontScale)}x")
                Slider(
                    value = settings.theme.fontScale,
                    onValueChange = { settings.setFontScale(it) },
                    valueRange = 0.8f..2.0f,
                )
                // Palet önizleme
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaletteSwatch(TermBg, "zemin")
                    PaletteSwatch(TermBlue, "vurgu")
                    PaletteSwatch(TermGreen, "ok")
                }
            }
        }

        // Kullanım
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kullanım", style = MaterialTheme.typography.titleMedium)
                usage.rows.forEach { u ->
                    Column {
                        Row {
                            Text(u.agent, Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                            Text("%${u.percent} • sıfırlanma ${u.resetIn}")
                        }
                        LinearProgressIndicator(
                            progress = { u.percent / 100f },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
                Text(
                    "Snapshot'lar 24 saat saklanır; backend tam transcript görmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Hakkında
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Hakkında", style = MaterialTheme.typography.titleMedium)
                Text("Pocket Agent 0.2.0 • GPL-3.0-or-later")
                Text(
                    "Terminal baytları, diff ve dosya içerikleri backend'den geçmez; yalnız kısa özetler (≤256 karakter, 24s TTL) tutulur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Dikte: cihaz-içi varsayılan (BYOK opt-in) • Deep link: pocketagent://tmux|herdr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PaletteSwatch(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
