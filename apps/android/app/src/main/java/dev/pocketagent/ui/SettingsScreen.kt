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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pocketagent.android.App
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.theme.TermBg
import dev.pocketagent.ui.theme.TermBlue
import dev.pocketagent.ui.theme.TermGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: SettingsViewModel, usage: UsageViewModel, hostKeys: TofuHostKeyStore, app: App) {
    var pinnedKeys by remember { mutableStateOf(hostKeys.all()) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AMOLED siyah", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Saf siyah zemin — OLED ekranda pil tasarrufu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.amoled,
                        onCheckedChange = { settings.toggleAmoled() },
                        enabled = settings.theme.dark,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Açılışta son oturuma bağlan", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Secret Keystore'da saklıysa uygulama açılır açılmaz yeniden bağlanır",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.autoReconnect, onCheckedChange = { settings.toggleAutoReconnect() })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Kopunca otomatik yeniden bağlan", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Bağlantı beklenmedik kapanırsa 5 denemeye kadar üstel geri çekilmeyle yeniden kurulur; kimlik/host-key hatasında durur",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.autoReconnectOnDrop, onCheckedChange = { settings.toggleAutoReconnectOnDrop() })
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

        // Backend (self-hosted)
        BackendCard(settings, app)

        // Kullanım
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kullanım", style = MaterialTheme.typography.titleMedium)
                if (usage.rows.isEmpty()) {
                    Text(
                        "Henüz kullanım verisi yok — host raporlama bağlanınca burada görünür. Backend ayarlıysa her senkron turunda güncellenir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

        // Bilinen host anahtarları (TOFU pin)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bilinen host anahtarları", style = MaterialTheme.typography.titleMedium)
                if (pinnedKeys.isEmpty()) {
                    Text(
                        "Henüz pinlenen anahtar yok. İlk bağlantıda parmak izi sorulur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    pinnedKeys.forEach { k ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${k.host}:${k.port}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    k.fingerprint,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { hostKeys.forget(k.host, k.port); pinnedKeys = hostKeys.all() }) {
                                Text("Unut", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Text(
                    "Pinlenen anahtar değişirse bağlantı durur (MITM koruması).",
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
                Text("Pocket Agent 0.10.1 • GPL-3.0-or-later")
                Text(
                    "Terminal fontu: JetBrains Mono 2.304 (OFL-1.1) • Lisans metni uygulama içi assets/licenses altında.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun BackendCard(settings: SettingsViewModel, app: App) {
    var url by remember { mutableStateOf(settings.backendUrl) }
    var tenant by remember { mutableStateOf(settings.tenantToken) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val syncStatus by app.eventSync.status.collectAsState()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backend (self-hosted)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; saved = false },
                label = { Text("Backend URL (https://agent.example.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tenant,
                onValueChange = { tenant = it; saved = false },
                label = { Text("Tenant token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    settings.setBackend(url, tenant)
                    app.configureBackend(url.trim(), tenant.trim())
                    saved = true
                    testResult = null
                }) { Text("Kaydet") }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val ok = app.backendClient?.health() == true
                            testResult = if (ok) "Backend erişilebilir" else "Ulaşılamadı"
                        }
                    },
                    enabled = settings.backendConfigured,
                ) { Text("Test et") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Event sync: $syncStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                testResult?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it == "Backend erişilebilir") TermGreen else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                "Backend yalnız kısa olay özetleri tutar (≤256 karakter, 24s TTL); terminal ve dosya içerikleri hiç gitmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
