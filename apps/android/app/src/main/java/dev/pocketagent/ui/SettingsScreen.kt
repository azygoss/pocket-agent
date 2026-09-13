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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg),
    ) {
        Spacer(Modifier.height(Space.lg))
        ScreenHeader("Ayarlar")
        Spacer(Modifier.height(Space.xl))

        // ── Görünüm: tema grid'i + font + ölçek tek derli kartta ──────────
        SectionLabel("Görünüm")
        Spacer(Modifier.height(Space.md))
        ConsoleCard {
            Text(
                "Tema",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            ConsoleThemes.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    row.forEach { t ->
                        ThemeCell(
                            t,
                            settings.theme.themeId == t.id,
                            Modifier.weight(1f),
                        ) { settings.setThemeId(t.id) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(Space.sm))
            }
            SoftDivider()
            Spacer(Modifier.height(Space.sm))
            Text(
                "Yazı tipi",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.sm))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                ConsoleFonts.forEach { f ->
                    FontChip(f, settings.theme.fontId == f.id) { settings.setFontId(f.id) }
                }
            }
        }
        Spacer(Modifier.height(Space.lg))
        ConsoleCard {
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
        Spacer(Modifier.height(Space.xxl))

        // ── Backend ────────────────────────────────────────────────────────
        BackendSection(settings, app)
        Spacer(Modifier.height(Space.xxl))

        // ── Tuş şeridi snippet'ları ────────────────────────────────────────
        SectionLabel("Tuş şeridi")
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
        Spacer(Modifier.height(Space.xxl))

        // ── Yedekleme / geri yükleme ───────────────────────────────────────
        BackupSection(settings, app)
        Spacer(Modifier.height(Space.xxl))

        // ── Kullanım ───────────────────────────────────────────────────────
        SectionLabel("Kullanım")
        Spacer(Modifier.height(Space.md))
        if (usage.rows.isEmpty()) {
            ConsoleCard {
                Text(
                    "Henüz kullanım verisi yok — host raporlama bağlanınca burada görünür. Backend ayarlıysa her senkron turunda güncellenir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ConsoleCard {
                usage.rows.forEachIndexed { i, u ->
                    if (i > 0) SoftDivider()
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
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            "Snapshot'lar 24 saat saklanır; backend tam transcript görmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xxl))

        // ── Bilinen host anahtarları (TOFU pin) ────────────────────────────
        SectionLabel("Bilinen host anahtarları")
        Spacer(Modifier.height(Space.sm))
        if (pinnedKeys.isEmpty()) {
            ConsoleCard {
                Text(
                    "Henüz pinlenen anahtar yok. İlk bağlantıda parmak izi sorulur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ConsoleCard {
                pinnedKeys.forEachIndexed { i, k ->
                    if (i > 0) SoftDivider()
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
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            "Pinlenen anahtar değişirse bağlantı durur (MITM koruması).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xxl))

        // ── Güncelleme: GitHub Releases kontrolü + indir/kur ──────────────
        SectionLabel("Güncelleme")
        Spacer(Modifier.height(Space.md))
        UpdateCard()
        Spacer(Modifier.height(Space.lg))

        // ── Hakkında ───────────────────────────────────────────────────────
        SectionLabel("Hakkında")
        Spacer(Modifier.height(Space.sm))
        // Sürüm manifest'ten okunur — derleme ile ekran asla desenkron olmaz.
        val pkg = androidx.compose.ui.platform.LocalContext.current.packageManager
        val version = remember {
            runCatching {
                pkg.getPackageInfo(app.packageName, 0).versionName
            }.getOrNull() ?: "dev"
        }
        ConsoleCard {
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
        Spacer(Modifier.height(Space.xxl))
    }
}

// Güncelleme kartı: mevcut sürüm + kontrol butonu; güncelleme varsa
// sürüm + boyut + "İndir ve kur" → ilerleme → paket kurucu.
private enum class UpdPhase { Idle, Checking, Current, Available, Downloading, Ready, Failed }

@Composable
private fun UpdateCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "dev"
    }
    var phase by remember { mutableStateOf(UpdPhase.Idle) }
    var rel by remember { mutableStateOf<dev.pocketagent.net.ReleaseInfo?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var err by remember { mutableStateOf<String?>(null) }

    fun check() {
        phase = UpdPhase.Checking
        err = null
        scope.launch(Dispatchers.IO) {
            runCatching { dev.pocketagent.net.UpdateChecker.fetchLatest() }
                .onSuccess { r ->
                    when {
                        r == null -> { phase = UpdPhase.Failed; err = "Sürüm bilgisi alınamadı" }
                        dev.pocketagent.net.UpdateChecker.isNewer(r.version, version) -> { rel = r; phase = UpdPhase.Available }
                        else -> phase = UpdPhase.Current
                    }
                }
                .onFailure { phase = UpdPhase.Failed; err = it.message }
        }
    }

    fun downloadAndInstall(r: dev.pocketagent.net.ReleaseInfo) {
        phase = UpdPhase.Downloading
        progress = 0f
        scope.launch(Dispatchers.IO) {
            val dest = java.io.File(context.cacheDir, "shared/pocket-agent-${r.version}.apk")
            runCatching {
                dev.pocketagent.net.UpdateChecker.download(r.apkUrl, dest) { progress = it }
            }.onSuccess {
                phase = UpdPhase.Ready
                dev.pocketagent.net.UpdateChecker.installApk(context, dest)
            }.onFailure {
                phase = UpdPhase.Available
                err = "İndirme başarısız: ${it.message}"
            }
        }
    }

    ConsoleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sürüm $version", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (phase) {
                        UpdPhase.Idle -> "GitHub Releases"
                        UpdPhase.Checking -> "Kontrol ediliyor…"
                        UpdPhase.Current -> "Güncel"
                        UpdPhase.Available -> rel?.let { "v${it.version} · %.0f MB".format(it.sizeBytes / 1e6f) } ?: ""
                        UpdPhase.Downloading -> "İndiriliyor %${(progress * 100).toInt()}"
                        UpdPhase.Ready -> rel?.let { "v${it.version} indirildi" } ?: ""
                        UpdPhase.Failed -> err ?: "Hata"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (phase) {
                        UpdPhase.Current -> TermGreen
                        UpdPhase.Failed -> MaterialTheme.colorScheme.error
                        UpdPhase.Available, UpdPhase.Ready -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            when (phase) {
                UpdPhase.Checking, UpdPhase.Downloading ->
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.width(20.dp).height(20.dp),
                        strokeWidth = 2.dp,
                    )
                UpdPhase.Available, UpdPhase.Ready ->
                    ConsoleButton(onClick = { rel?.let(::downloadAndInstall) }) { Text("İndir ve kur") }
                else ->
                    ConsoleOutlinedButton(onClick = ::check) { Text("Kontrol et") }
            }
        }
        if (phase == UpdPhase.Downloading) {
            Spacer(Modifier.height(Space.sm))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        rel?.notes?.takeIf { it.isNotBlank() && phase == UpdPhase.Available }?.let {
            Spacer(Modifier.height(Space.sm))
            Text(
                it.lines().take(3).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}

// Tema hücresi: grid'de kompakt mini terminal önizlemesi — zemin rengi +
// accent çizgisi + isim. Seçili hücre accent halkası alır.
@Composable
private fun ThemeCell(t: ConsoleTheme, selected: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val mono = dev.pocketagent.ui.theme.LocalMonoFont.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color(t.term.background))
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                },
            )
            .clickable(onClick = onTap)
            .padding(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("❯", color = Color(t.accent), fontFamily = mono, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(14.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(t.accent)),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text("src/  notes.md", color = Color(t.term.foreground), fontFamily = mono, fontSize = 7.5.sp, maxLines = 1)
        Text("main.go  ok", color = Color(t.term.ansi[2]), fontFamily = mono, fontSize = 7.5.sp, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(
            t.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else Color(t.term.foreground).copy(alpha = 0.75f),
            maxLines = 1,
        )
    }
}

// Font çipi: adı kendi fontuyla render eder (canlı önizleme). Tonal kapsül;
// seçiliyken accent tint.
@Composable
private fun FontChip(f: ConsoleFont, selected: Boolean, onTap: () -> Unit) {
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
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
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}

// Yedekleme bölümü: bağlantılar + ayarlar JSON'a dışa aktarılır (secret'lar
// asla dahil değil); içe aktarım mevcut listeye ekler, silmez.
@Composable
private fun BackupSection(settings: SettingsViewModel, app: App) {
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

    SectionLabel("Yedekleme")
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

@Composable
private fun BackendSection(settings: SettingsViewModel, app: App) {
    var url by remember { mutableStateOf(settings.backendUrl) }
    var tenant by remember { mutableStateOf(settings.tenantToken) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val syncStatus by app.eventSync.status.collectAsState()

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Backend (self-hosted)")
        Spacer(Modifier.weight(1f))
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
