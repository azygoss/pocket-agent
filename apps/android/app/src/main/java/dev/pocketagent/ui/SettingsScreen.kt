// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pocketagent.android.App
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.theme.ConsoleFont
import dev.pocketagent.ui.theme.ConsoleFonts
import dev.pocketagent.ui.theme.ConsoleTheme
import dev.pocketagent.ui.theme.ConsoleThemes
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: SettingsViewModel, usage: UsageViewModel, hostKeys: TofuHostKeyStore, app: App) {
    var pinnedKeys by remember { mutableStateOf(hostKeys.all()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        // ── Görünüm: tema + font ───────────────────────────────────────────
        ConsoleCard {
            CardHeader("Görünüm")
            Spacer(Modifier.height(Space.md))
            SectionLabel("Tema")
            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                ConsoleThemes.forEach { t ->
                    ThemeCard(t, settings.theme.themeId == t.id) { settings.setThemeId(t.id) }
                }
            }
            Spacer(Modifier.height(Space.lg))
            SectionLabel("Font")
            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                ConsoleFonts.forEach { f ->
                    FontChip(f, settings.theme.fontId == f.id) { settings.setFontId(f.id) }
                }
            }
            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Yazı ölçeği", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    "%.1fx".format(settings.theme.fontScale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = settings.theme.fontScale,
                onValueChange = { settings.setFontScale(it) },
                valueRange = 0.8f..2.0f,
            )
            Spacer(Modifier.height(Space.sm))
            SoftDivider()
            SettingRow(
                "Açılışta son oturuma bağlan",
                subtitle = "Secret Keystore'da saklıysa uygulama açılır açılmaz bağlanır",
            ) {
                Switch(checked = settings.autoReconnect, onCheckedChange = { settings.toggleAutoReconnect() })
            }
            SoftDivider()
            SettingRow(
                "Kopunca otomatik yeniden bağlan",
                subtitle = "5 denemeye kadar üstel geri çekilme; kimlik/host-key hatasında durur",
            ) {
                Switch(checked = settings.autoReconnectOnDrop, onCheckedChange = { settings.toggleAutoReconnectOnDrop() })
            }
        }

        // ── Backend ────────────────────────────────────────────────────────
        BackendCard(settings, app)

        // ── Tuş şeridi snippet'ları ────────────────────────────────────────
        ConsoleCard {
            CardHeader("Tuş şeridi")
            Spacer(Modifier.height(Space.sm))
            Text(
                "Sık komutlar terminalin alt şeridine tuş olarak eklenir. Her satır: etiket=komut (örn. gs=git status).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            var snip by remember { mutableStateOf(settings.snippets) }
            OutlinedTextField(
                value = snip,
                onValueChange = { snip = it },
                label = { Text("Snippet'lar") },
                placeholder = { Text("gs=git status\nht=htop\nta=tmux attach") },
                minLines = 3, maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            if (snip != settings.snippets) {
                ConsoleButton(
                    onClick = { settings.updateSnippets(snip) },
                    modifier = Modifier.padding(top = Space.sm),
                ) { Text("Kaydet") }
            }
        }

        // ── Yedekleme / geri yükleme ───────────────────────────────────────
        BackupCard(settings, app)

        // ── Kullanım ───────────────────────────────────────────────────────
        ConsoleCard {
            CardHeader("Kullanım")
            Spacer(Modifier.height(Space.md))
            if (usage.rows.isEmpty()) {
                Text(
                    "Henüz kullanım verisi yok — host raporlama bağlanınca burada görünür. Backend ayarlıysa her senkron turunda güncellenir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            usage.rows.forEach { u ->
                Column(Modifier.padding(vertical = Space.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u.agent, Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "%${u.percent} · ${u.resetIn}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Space.sm))
                    MeterBar(u.percent / 100f)
                }
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "Snapshot'lar 24 saat saklanır; backend tam transcript görmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Bilinen host anahtarları (TOFU pin) ────────────────────────────
        ConsoleCard {
            CardHeader("Bilinen host anahtarları")
            Spacer(Modifier.height(Space.sm))
            if (pinnedKeys.isEmpty()) {
                Text(
                    "Henüz pinlenen anahtar yok. İlk bağlantıda parmak izi sorulur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                pinnedKeys.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.sm)) {
                        Column(Modifier.weight(1f)) {
                            Text("${k.host}:${k.port}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                k.fingerprint,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ConsoleTextButton(onClick = { hostKeys.forget(k.host, k.port); pinnedKeys = hostKeys.all() }) {
                            Text("Unut", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                "Pinlenen anahtar değişirse bağlantı durur (MITM koruması).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Hakkında ───────────────────────────────────────────────────────
        ConsoleCard {
            CardHeader("Hakkında")
            Spacer(Modifier.height(Space.sm))
            // Sürüm manifest'ten okunur — derleme ile ekran asla desenkron olmaz.
            val pkg = androidx.compose.ui.platform.LocalContext.current.packageManager
            val version = remember {
                runCatching {
                    pkg.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull() ?: "dev"
            }
            Text("Pocket Agent $version • GPL-3.0-or-later", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Space.xs))
            Text(
                "Fontlar: JetBrains Mono, IBM Plex Mono, Space Mono (OFL-1.1) • Lisans metinleri assets/licenses altında.",
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
        Spacer(Modifier.height(Space.sm))
    }
}

// Tema kartı: temanın zemin/vurgu renkleriyle mini önizleme.
@Composable
private fun ThemeCard(t: ConsoleTheme, selected: Boolean, onTap: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .width(124.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(t.background))
            .border(if (selected) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(onClick = onTap)
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            SwatchDot(Color(t.term.background), MaterialTheme.colorScheme.outline)
            SwatchDot(Color(t.accent), null)
            SwatchDot(Color(t.accentAlt), null)
            SwatchDot(Color(t.term.foreground), null)
        }
        Spacer(Modifier.height(Space.sm))
        Text(t.name, style = MaterialTheme.typography.labelSmall, color = Color(t.text), maxLines = 1)
    }
}

@Composable
private fun SwatchDot(color: Color, border: Color?) {
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier),
    )
}

// Font çipi: adı kendi fontuyla render eder (canlı önizleme).
@Composable
private fun FontChip(f: ConsoleFont, selected: Boolean, onTap: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .clickable(onClick = onTap)
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Text(f.name, fontFamily = f.family, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

// İnce ölçüm çubuğu: dolgu oranı + eşikte renk değişimi.
@Composable
private fun MeterBar(fraction: Float) {
    val f = fraction.coerceIn(0f, 1f)
    val color = if (f >= 0.85f) TermRed else MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}

// Yedekleme kartı: bağlantılar + ayarlar JSON'a dışa aktarılır (secret'lar
// asla dahil değil); içe aktarım mevcut listeye ekler, silmez.
@Composable
private fun BackupCard(settings: SettingsViewModel, app: App) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    val conns by app.connections.items.collectAsState()

    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                val json = dev.pocketagent.data.Backup.export(
                    conns,
                    dev.pocketagent.data.PersistedSettings(
                        fontScale = settings.theme.fontScale,
                        backendUrl = settings.backendUrl,
                        autoReconnect = settings.autoReconnect,
                        autoReconnectOnDrop = settings.autoReconnectOnDrop,
                        themeId = settings.theme.themeId,
                        fontId = settings.theme.fontId,
                        snippets = settings.snippets,
                    ),
                )
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess { msg = "Yedek yazıldı (${conns.size} bağlantı)" }
                .onFailure { msg = "Yedek yazılamadı: ${it.message}" }
        }
    }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                val r = dev.pocketagent.data.Backup.import(text)
                r.connections.forEach { app.connections.upsert(it, null) }
                r.settings?.let { settings.applyAll(it) }
                r
            }.onSuccess { msg = "${it.connections.size} bağlantı içe aktarıldı" + (if (it.skipped > 0) " (${it.skipped} atlandı)" else "") }
                .onFailure { msg = "İçe aktarım başarısız: ${it.message}" }
        }
    }

    ConsoleCard {
        CardHeader("Yedekleme")
        Spacer(Modifier.height(Space.sm))
        Text(
            "Bağlantılar ve ayarlar JSON olarak dışa aktarılır. Parolalar ve anahtarlar hiçbir zaman yedeğe girmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            ConsoleButton(onClick = { exporter.launch("pocket-agent-backup.json") }) { Text("Dışa aktar") }
            ConsoleOutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("İçe aktar") }
            msg?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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

    ConsoleCard {
        CardHeader("Backend (self-hosted)") {
            TagPill(syncStatus, active = syncStatus == "bağlı", tone = if (syncStatus == "bağlı") TermGreen else null)
        }
        Spacer(Modifier.height(Space.md))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; saved = false },
            label = { Text("Backend URL (https://agent.example.com)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.sm))
        OutlinedTextField(
            value = tenant,
            onValueChange = { tenant = it; saved = false },
            label = { Text("Tenant token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            ConsoleButton(onClick = {
                settings.setBackend(url, tenant)
                app.configureBackend(url.trim(), tenant.trim())
                saved = true
                testResult = null
            }) { Text("Kaydet") }
            ConsoleOutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val ok = app.backendClient?.health() == true
                        testResult = if (ok) "Backend erişilebilir" else "Ulaşılamadı"
                    }
                },
                enabled = settings.backendConfigured,
            ) { Text("Test et") }
            testResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it == "Backend erişilebilir") TermGreen else MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "Backend yalnız kısa olay özetleri tutar (≤256 karakter, 24s TTL); terminal ve dosya içerikleri hiç gitmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
