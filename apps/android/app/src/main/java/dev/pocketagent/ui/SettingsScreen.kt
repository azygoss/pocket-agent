// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.android.App
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.theme.ConsoleFont
import dev.pocketagent.ui.theme.ConsoleFonts
import dev.pocketagent.ui.theme.ConsoleTheme
import dev.pocketagent.ui.theme.ConsoleThemes
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.Readout
import androidx.compose.foundation.layout.defaultMinSize
import dev.pocketagent.ui.theme.consoleFont
import dev.pocketagent.ui.theme.consoleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Ayarlar: hub-and-spoke. Index, her bölümü mevcut değeriyle gösteren
// gruplu satırlardan oluşur; detay sayfalar tek konuya odaklanır.
private enum class SettingsPage(val title: String) {
    Appearance("Görünüm"),
    Session("Terminal ve oturum"),
    Backend("Backend"),
    Security("Bilinen host anahtarları"),
    Data("Yedekleme ve kullanım"),
    About("Hakkında"),
}

@Composable
fun SettingsScreen(settings: SettingsViewModel, usage: UsageViewModel, hostKeys: TofuHostKeyStore, app: App) {
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val back = { page = null }
    // Detaya giriş: içerik 24dp sağdan kayarak + solarak gelir; dönüş tersi.
    val reduced = dev.pocketagent.ui.theme.rememberReducedMotion()
    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            if (reduced) {
                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            } else {
                (
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) +
                        androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(220)) { w -> if (forward) w / 16 else -w / 16 }
                    ) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90))
            }
        },
        label = "settings-page",
    ) { current ->
        when (current) {
            null -> SettingsIndex(settings, usage, hostKeys, app) { page = it }
            SettingsPage.Appearance -> SettingsDetailPage(SettingsPage.Appearance.title, back) { AppearanceContent(settings) }
            SettingsPage.Session -> SettingsDetailPage(SettingsPage.Session.title, back) { SessionContent(settings) }
            SettingsPage.Backend -> SettingsDetailPage(SettingsPage.Backend.title, back) { BackendContent(settings, app) }
            SettingsPage.Security -> SettingsDetailPage(SettingsPage.Security.title, back) { SecurityContent(hostKeys) }
            SettingsPage.Data -> SettingsDetailPage(SettingsPage.Data.title, back) { DataContent(settings, usage, app) }
            SettingsPage.About -> SettingsDetailPage(SettingsPage.About.title, back) { AboutContent(app) }
        }
    }
}

// ── Index ────────────────────────────────────────────────────────────────────
// Gruplu nav satırları: ikon karosu + başlık + canlı değer özeti + chevron.
// Kullanıcı detaya girmeden ayarın mevcut durumunu görür.

@Composable
private fun SettingsIndex(
    settings: SettingsViewModel,
    usage: UsageViewModel,
    hostKeys: TofuHostKeyStore,
    app: App,
    onOpen: (SettingsPage) -> Unit,
) {
    val theme = consoleTheme(settings.theme.themeId)
    val font = consoleFont(settings.theme.fontId)
    val syncStatus by app.eventSync.status.collectAsState()
    val pinned = hostKeys.all()
    val pkg = LocalContext.current.packageManager
    val version = remember {
        runCatching { pkg.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "dev"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg),
    ) {
        Spacer(Modifier.height(Space.lg))
        ScreenHeader("Ayarlar", meta = "v$version")
        Spacer(Modifier.height(Space.xl))

        SettingsGroup("Kişiselleştirme") {
            SettingsNavRow(
                icon = Icons.Filled.Palette,
                title = "Görünüm",
                subtitle = "${theme.name} · ${font.name} · %.1fx".format(settings.theme.fontScale),
            ) { onOpen(SettingsPage.Appearance) }
            InsetDivider()
            SettingsNavRow(
                icon = Icons.Filled.Terminal,
                title = "Terminal ve oturum",
                subtitle = sessionSubtitle(settings),
            ) { onOpen(SettingsPage.Session) }
        }
        Spacer(Modifier.height(Space.xl))

        SettingsGroup("Bağlantı") {
            SettingsNavRow(
                icon = Icons.Filled.Dns,
                title = "Backend",
                subtitle = backendSubtitle(settings.backendUrl),
                trailing = if (settings.backendConfigured) {
                    { TagPill(syncStatus, active = syncStatus == "bağlı", tone = if (syncStatus == "bağlı") Tok.accent else null) }
                } else {
                    null
                },
            ) { onOpen(SettingsPage.Backend) }
            InsetDivider()
            SettingsNavRow(
                icon = Icons.Filled.Key,
                title = "Bilinen host anahtarları",
                subtitle = if (pinned.isEmpty()) "Henüz pinli anahtar yok" else "${pinned.size} anahtar pinlendi",
            ) { onOpen(SettingsPage.Security) }
        }
        Spacer(Modifier.height(Space.xl))

        SettingsGroup("Uygulama") {
            SettingsNavRow(
                icon = Icons.Filled.Backup,
                title = "Yedekleme ve kullanım",
                subtitle = if (usage.rows.isEmpty()) "JSON dışa aktarım · kota özetleri" else "${usage.rows.size} agent izleniyor",
            ) { onOpen(SettingsPage.Data) }
            InsetDivider()
            SettingsNavRow(
                icon = Icons.Filled.Info,
                title = "Hakkında",
                subtitle = "v$version · güncelleme · lisanslar",
            ) { onOpen(SettingsPage.About) }
        }
        Spacer(Modifier.height(Space.xxl))
    }
}

private fun sessionSubtitle(s: SettingsViewModel): String {
    val n = s.snippetList().size
    val snip = if (n == 0) "snippet yok" else "$n snippet"
    val rec = if (s.autoReconnectOnDrop) "yeniden bağlan açık" else "yeniden bağlan kapalı"
    return "$snip · $rec"
}

private fun backendSubtitle(url: String): String {
    if (url.isBlank()) return "Ayarlanmadı"
    return url.removePrefix("https://").removePrefix("http://").substringBefore('/').ifBlank { "Ayarlanmadı" }
}

// Bölüm: küçük etiket + tek kartta toplanan satırlar (iOS grouped list dili).
@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(label, Modifier.padding(start = 2.dp))
    Spacer(Modifier.height(Space.sm))
    ConsoleCard(padding = 0.dp, content = content)
}

// Ayırıcıyı metin hizasına içeri al (ikon karosu genişliği kadar girinti).
@Composable
private fun InsetDivider() {
    SoftDivider(Modifier.padding(start = Space.lg + 36.dp + Space.md))
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = { IconTile(icon) },
        trailing = {
            trailing?.invoke()
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Tok.muted,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

// ── Detay sayfası iskeleti ───────────────────────────────────────────────────
// Üstte 48dp geri hedefi + başlık; içerik kartları aşağıda.

@Composable
private fun SettingsDetailPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, top = Space.sm, end = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = "Geri" }
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Tok.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(Space.md))
            content()
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

// ── Görünüm ─────────────────────────────────────────────────────────────────

@Composable
private fun AppearanceContent(settings: SettingsViewModel) {
    ConsoleCard {
        SectionLabel("Tema")
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
    }
    Spacer(Modifier.height(Space.lg))
    ConsoleCard {
        SectionLabel("Terminal yazı tipi")
        Spacer(Modifier.height(2.dp))
        Text(
            "Arayüz okumaları her zaman IBM Plex Mono ile basılır; bu seçim terminali etkiler.",
            style = MaterialTheme.typography.bodySmall,
            color = Tok.muted,
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
        SoftDivider(Modifier.padding(vertical = Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Yazı ölçeği", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "%.1fx".format(settings.theme.fontScale),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = Readout),
                color = Tok.text2,
            )
        }
        Slider(
            value = settings.theme.fontScale,
            onValueChange = { settings.setFontScale(it) },
            valueRange = 0.8f..2.0f,
        )
    }
}

// ── Terminal ve oturum ───────────────────────────────────────────────────────

@Composable
private fun SessionContent(settings: SettingsViewModel) {
    ConsoleCard {
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
    Spacer(Modifier.height(Space.lg))
    ConsoleCard {
        SectionLabel("Tuş şeridi snippet'ları")
        Spacer(Modifier.height(Space.xs))
        Text(
            "Sık komutlar terminalin alt şeridine tuş olarak eklenir. Her satır: etiket=komut.",
            style = MaterialTheme.typography.bodySmall,
            color = Tok.muted,
        )
        Spacer(Modifier.height(Space.sm))
        var snip by remember { mutableStateOf(settings.snippets) }
        OutlinedTextField(
            value = snip,
            onValueChange = { snip = it },
            placeholder = { Text("gs=git status\nht=htop\nta=tmux attach", fontFamily = Readout) },
            minLines = 3, maxLines = 6,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Readout),
            modifier = Modifier.fillMaxWidth(),
        )
        if (snip != settings.snippets) {
            ConsoleButton(
                onClick = { settings.updateSnippets(snip) },
                modifier = Modifier.padding(top = Space.sm),
            ) { Text("Kaydet") }
        }
    }
}

// ── Backend ──────────────────────────────────────────────────────────────────

@Composable
private fun BackendContent(settings: SettingsViewModel, app: App) {
    var url by remember { mutableStateOf(settings.backendUrl) }
    var tenant by remember { mutableStateOf(settings.tenantToken) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val syncStatus by app.eventSync.status.collectAsState()

    fun save() {
        val u = url.trim()
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
            urlError = "http:// veya https:// ile başlamalı"
            return
        }
        urlError = null
        settings.setBackend(u, tenant)
        app.configureBackend(u, tenant.trim())
        saved = true
        testResult = null
    }

    ConsoleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Durum", style = MaterialTheme.typography.bodyLarge, color = Tok.text)
            Spacer(Modifier.weight(1f))
            TagPill(
                if (settings.backendConfigured) syncStatus else "ayarlanmadı",
                active = syncStatus == "bağlı",
                tone = if (syncStatus == "bağlı") Tok.accent else null,
            )
        }
        SoftDivider(Modifier.padding(vertical = Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Backend yalnız kısa olay özetleri tutar (≤256 karakter, 24s TTL); terminal ve dosya içerikleri hiç gitmez.",
                style = MaterialTheme.typography.bodySmall,
                color = Tok.muted,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(Space.lg))
    ConsoleCard {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; urlError = null; saved = false },
            label = { Text("Backend URL") },
            placeholder = { Text("https://agent.example.com", fontFamily = Readout) },
            mono = true,
            supportingText = { urlError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
            isError = urlError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.sm))
        OutlinedTextField(
            value = tenant,
            onValueChange = { tenant = it; saved = false },
            label = { Text("Tenant token") },
            mono = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            ConsoleButton(onClick = ::save) { Text("Kaydet") }
            ConsoleOutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val ok = app.backendClient?.health() == true
                        testResult = if (ok) "Backend erişilebilir" else "Ulaşılamadı"
                    }
                },
                enabled = settings.backendConfigured,
            ) { Text("Test et") }
            if (saved) {
                Text("Kaydedildi", style = MaterialTheme.typography.bodySmall, color = Tok.success)
            } else {
                testResult?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it == "Backend erişilebilir") Tok.success else Tok.danger,
                    )
                }
            }
        }
    }
}

// ── Bilinen host anahtarları ─────────────────────────────────────────────────

@Composable
private fun SecurityContent(hostKeys: TofuHostKeyStore) {
    var pinnedKeys by remember { mutableStateOf(hostKeys.all()) }
    if (pinnedKeys.isEmpty()) {
        ConsoleCard {
            Text(
                "Henüz pinlenen anahtar yok. İlk bağlantıda parmak izi sorulur.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tok.muted,
            )
        }
    } else {
        ConsoleCard(padding = 0.dp) {
            pinnedKeys.forEachIndexed { i, k ->
                if (i > 0) SoftDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${k.host}:${k.port}", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Readout), color = Tok.text)
                        Text(
                            k.fingerprint,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout),
                            color = Tok.muted,
                        )
                    }
                    ConsoleTextButton(onClick = { hostKeys.forget(k.host, k.port); pinnedKeys = hostKeys.all() }) {
                        Text("Unut", color = Tok.danger)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(Space.sm))
    Text(
        "Pinlenen anahtar değişirse bağlantı durur (MITM koruması).",
        style = MaterialTheme.typography.bodySmall,
        color = Tok.muted,
    )
}

// ── Yedekleme ve kullanım ────────────────────────────────────────────────────

@Composable
private fun DataContent(settings: SettingsViewModel, usage: UsageViewModel, app: App) {
    val context = LocalContext.current
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
        Spacer(Modifier.height(Space.xs))
        Text(
            "Bağlantılar ve ayarlar JSON olarak dışa aktarılır. Parolalar ve anahtarlar hiçbir zaman yedeğe girmez.",
            style = MaterialTheme.typography.bodySmall,
            color = Tok.muted,
        )
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            ConsoleButton(onClick = { exporter.launch("pocket-agent-backup.json") }) { Text("Dışa aktar") }
            ConsoleOutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("İçe aktar") }
        }
        msg?.let {
            Spacer(Modifier.height(Space.sm))
            Text(it, style = MaterialTheme.typography.labelSmall, color = Tok.muted)
        }
    }
    Spacer(Modifier.height(Space.lg))

    ConsoleCard {
        CardHeader("Kullanım")
        Spacer(Modifier.height(Space.sm))
        if (usage.rows.isEmpty()) {
            Text(
                "Henüz kullanım verisi yok — host raporlama bağlanınca burada görünür. Backend ayarlıysa her senkron turunda güncellenir.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tok.muted,
            )
        } else {
            usage.rows.forEachIndexed { i, u ->
                if (i > 0) SoftDivider(Modifier.padding(vertical = Space.sm)) else Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.agent, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Readout), color = Tok.text)
                    Text(
                        "%${u.percent} · ${u.resetIn}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
                        color = Tok.muted,
                    )
                }
                Spacer(Modifier.height(Space.sm))
                CellMeter(u.percent / 100f)
            }
        }
    }
    Spacer(Modifier.height(Space.sm))
    Text(
        "Snapshot'lar 24 saat saklanır; backend tam transcript görmez.",
        style = MaterialTheme.typography.bodySmall,
        color = Tok.muted,
    )
}

// ── Hakkında ─────────────────────────────────────────────────────────────────

@Composable
private fun AboutContent(app: App) {
    val pkg = LocalContext.current.packageManager
    val version = remember {
        runCatching { pkg.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "dev"
    }
    AboutHero(version)
    UpdateCard()
    Spacer(Modifier.height(Space.xl))
    // Künye: anahtar/değer satırları — paragraf duvarı yerine taranabilir.
    SectionLabel("Künye", Modifier.padding(start = 2.dp))
    Spacer(Modifier.height(Space.sm))
    ConsoleCard(padding = 0.dp) {
        AboutRow("Lisans", "GPL-3.0-or-later")
        AboutRow("Fontlar", "JetBrains Mono · IBM Plex Mono · Space Mono (OFL-1.1)")
        AboutRow("Gizlilik", "Terminal, diff ve dosyalar backend'e gitmez; yalnız ≤256 karakter özet, 24s TTL")
        AboutRow("Dikte", "cihaz-içi varsayılan · BYOK opt-in")
        AboutRow("Deep link", "pocketagent://tmux|herdr", last = true)
    }
}

// Hakkında hero'su: büyük piksel işareti + ad + sürüm okuması.
@Composable
private fun AboutHero(version: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Tok.chrome)
                .border(1.dp, Tok.border, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) { PocketMark(size = 44.dp) }
        Spacer(Modifier.height(Space.lg))
        Text("Pocket Agent", style = MaterialTheme.typography.headlineSmall, color = Tok.text)
        Spacer(Modifier.height(2.dp))
        Text(
            "v$version · cepten self-hosted terminal",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout),
            color = Tok.muted,
        )
    }
}

@Composable
private fun AboutRow(key: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = Tok.muted, modifier = Modifier.width(92.dp))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout), color = Tok.text, modifier = Modifier.weight(1f))
    }
    if (!last) SoftDivider(Modifier.padding(start = Space.lg))
}

// Güncelleme kartı: mevcut sürüm + kontrol butonu; güncelleme varsa
// sürüm + boyut + "İndir ve kur" → ilerleme → paket kurucu.
private enum class UpdPhase { Idle, Checking, Current, Available, Downloading, Ready, Failed }

@Composable
private fun UpdateCard() {
    val context = LocalContext.current
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
        CardHeader("Güncelleme")
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sürüm $version", style = MaterialTheme.typography.bodyLarge, color = Tok.text)
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
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout),
                    color = when (phase) {
                        UpdPhase.Current -> Tok.success
                        UpdPhase.Failed -> Tok.danger
                        UpdPhase.Available, UpdPhase.Ready -> Tok.accent
                        else -> Tok.muted
                    },
                )
            }
            when (phase) {
                UpdPhase.Checking, UpdPhase.Downloading ->
                    BusyPixel(modifier = Modifier.padding(end = 8.dp))
                UpdPhase.Available, UpdPhase.Ready ->
                    ConsoleButton(onClick = { rel?.let(::downloadAndInstall) }) { Text("İndir ve kur") }
                else ->
                    ConsoleOutlinedButton(onClick = ::check) { Text("Kontrol et") }
            }
        }
        if (phase == UpdPhase.Downloading) {
            Spacer(Modifier.height(Space.sm))
            CellMeter(progress, warnAt = 2f)
        }
        rel?.notes?.takeIf { it.isNotBlank() && phase == UpdPhase.Available }?.let {
            Spacer(Modifier.height(Space.sm))
            Text(
                it.lines().take(3).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = Tok.muted,
                maxLines = 3,
            )
        }
    }
}

// Tema hücresi: grid'de kompakt mini terminal önizlemesi — zemin rengi +
// accent çizgisi + isim. Seçili hücre accent halkası alır.
@Composable
private fun ThemeCell(t: ConsoleTheme, selected: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val mono = Readout
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color(t.term.background))
            .then(
                // Seçim tek işaret: mürekkep çerçeve (mavi sinyale saklı).
                if (selected) {
                    Modifier.border(2.dp, Tok.text, MaterialTheme.shapes.small)
                } else {
                    Modifier.border(1.dp, Tok.border, MaterialTheme.shapes.small)
                },
            )
            .clickable(onClick = onTap)
            .padding(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("›", color = Color(t.accent), fontFamily = mono, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            listOf(t.accent, t.warning, t.error).forEach { c ->
                Box(
                    Modifier
                        .padding(start = 2.dp)
                        .size(5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(c)),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text("src/  notes.md", color = Color(t.term.foreground), fontFamily = mono, fontSize = 7.5.sp, maxLines = 1)
        Text("main.go  ok", color = Color(t.term.ansi[2]), fontFamily = mono, fontSize = 7.5.sp, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.name,
                style = MaterialTheme.typography.labelSmall,
                color = Color(t.term.foreground).copy(alpha = if (selected) 1f else 0.75f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            // Seçim biçimle de söylenir (çerçeve + tik) — renk tek ipucu değil.
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Seçili",
                    tint = Color(t.term.foreground),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

// Font çipi: adı kendi fontuyla render eder (canlı önizleme). Seçim
// yalnız zeminle — renk değişmez.
@Composable
private fun FontChip(f: ConsoleFont, selected: Boolean, onTap: () -> Unit) {
    Box(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) Tok.active else Color.Transparent)
            .border(1.dp, if (selected) Tok.borderStrong else Tok.border, MaterialTheme.shapes.small)
            .clickable(onClick = onTap)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = Space.md, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(f.name, fontFamily = f.family, style = MaterialTheme.typography.labelLarge, color = if (selected) Tok.text else Tok.text2, maxLines = 1)
    }
}
