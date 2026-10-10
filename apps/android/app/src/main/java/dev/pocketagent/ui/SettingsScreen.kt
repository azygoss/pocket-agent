// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.android.App
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.theme.Code
import dev.pocketagent.ui.theme.ConsoleFonts
import dev.pocketagent.ui.theme.ConsoleTheme
import dev.pocketagent.ui.theme.ConsoleThemes
import dev.pocketagent.ui.theme.Motion
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.Tone
import dev.pocketagent.ui.theme.consoleFont
import dev.pocketagent.ui.theme.consoleTheme
import dev.pocketagent.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Ayarlar: dizin + odaklı detay sayfaları. Dizin her bölümün mevcut değerini
// gösterir; kullanıcı içeri girmeden durumu görür.
private enum class SettingsPage(val title: String) {
    Appearance("Görünüm"),
    Session("Terminal ve oturum"),
    Backend("Backend"),
    Security("Bilinen host anahtarları"),
    Data("Yedekleme ve kullanım"),
    About("Hakkında"),
}

@Composable
fun SettingsScreen(
    settings: SettingsViewModel,
    usage: UsageViewModel,
    hostKeys: TofuHostKeyStore,
    app: App,
    onBack: (() -> Unit)? = null,
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(tween(Motion.Standard, easing = Motion.Enter)) +
                    slideInHorizontally(tween(Motion.Emphasized, easing = Motion.Enter)) { w -> if (forward) w / 10 else -w / 10 }) togetherWith
                    (fadeOut(tween(Motion.Fast, easing = Motion.Exit)) +
                        slideOutHorizontally(tween(Motion.Fast, easing = Motion.Exit)) { w -> if (forward) -w / 20 else w / 20 })
            }
        },
        label = "settings-page",
        modifier = Modifier.fillMaxSize().background(Tok.bg),
    ) { current ->
        when (current) {
            null -> SettingsIndex(settings, usage, hostKeys, app, onBack) { page = it }
            else -> DetailPage(current.title, onBack = { page = null }) {
                when (current) {
                    SettingsPage.Appearance -> AppearanceContent(settings)
                    SettingsPage.Session -> SessionContent(settings)
                    SettingsPage.Backend -> BackendContent(settings, app)
                    SettingsPage.Security -> SecurityContent(hostKeys)
                    SettingsPage.Data -> DataContent(settings, usage, app)
                    SettingsPage.About -> AboutContent(app)
                }
            }
        }
    }
}

// ── Dizin ───────────────────────────────────────────────────────────────────

@Composable
private fun SettingsIndex(
    settings: SettingsViewModel,
    usage: UsageViewModel,
    hostKeys: TofuHostKeyStore,
    app: App,
    onBack: (() -> Unit)?,
    onOpen: (SettingsPage) -> Unit,
) {
    val t = Tok
    val theme = consoleTheme(settings.theme.themeId)
    val font = consoleFont(settings.theme.fontId)
    val syncStatus by app.eventSync.status.collectAsState()
    val pinned = remember { hostKeys.all() }
    val version = rememberVersion(app)

    Column(Modifier.fillMaxSize()) {
        PocketTopBar("Ayarlar", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Column(Modifier.widthIn(max = 640.dp).align(Alignment.CenterHorizontally)) {
                SettingsGroup("Kişiselleştirme") {
                    NavRow(Icons.Rounded.Palette, t.accent, "Görünüm", "${theme.name} · ${font.name} · %.1f×".format(settings.theme.fontScale)) {
                        onOpen(SettingsPage.Appearance)
                    }
                    InsetDivider(Space.lg + 40.dp + Space.md)
                    NavRow(Icons.Rounded.Terminal, t.busy, "Terminal ve oturum", sessionSubtitle(settings)) { onOpen(SettingsPage.Session) }
                }
                SettingsGroup("Bağlantı") {
                    NavRow(
                        Icons.Rounded.Dns, t.live, "Backend", backendSubtitle(settings.backendUrl),
                        trailing = if (settings.backendConfigured) {
                            { val (s, l) = syncLabel(syncStatus); StatusChip(s, l) }
                        } else null,
                    ) { onOpen(SettingsPage.Backend) }
                    InsetDivider(Space.lg + 40.dp + Space.md)
                    NavRow(
                        Icons.Rounded.Key, t.warn, "Bilinen host anahtarları",
                        if (pinned.isEmpty()) "Henüz pinlenmiş anahtar yok" else "${pinned.size} anahtar pinli",
                    ) { onOpen(SettingsPage.Security) }
                }
                SettingsGroup("Uygulama") {
                    NavRow(
                        Icons.Rounded.Backup, t.neutral, "Yedekleme ve kullanım",
                        if (usage.rows.isEmpty()) "JSON yedek · agent kotaları" else "${usage.rows.size} agent izleniyor",
                    ) { onOpen(SettingsPage.Data) }
                    InsetDivider(Space.lg + 40.dp + Space.md)
                    NavRow(Icons.Rounded.Info, t.neutral, "Hakkında", "Sürüm $version · güncelleme · lisanslar") { onOpen(SettingsPage.About) }
                }
                Spacer(Modifier.height(Space.x3))
            }
        }
    }
}

private fun sessionSubtitle(s: SettingsViewModel): String {
    val n = s.snippetList().size
    val snip = if (n == 0) "kısayol yok" else "$n kısayol"
    val rec = if (s.autoReconnectOnDrop) "otomatik yeniden bağlanır" else "yeniden bağlanma kapalı"
    return listOfNotNull(rec, if (s.keepScreenOn) "ekran açık" else null, snip).joinToString(" · ")
}

private fun backendSubtitle(url: String): String {
    if (url.isBlank()) return "Ayarlanmadı"
    return url.removePrefix("https://").removePrefix("http://").substringBefore('/').ifBlank { "Ayarlanmadı" }
}

private fun syncLabel(status: String): Pair<Status, String> = when {
    status == "bağlı" -> Status.Live to "Bağlı"
    status == "kapalı" || status == "backend ayarlanmadı" -> Status.Idle to "Kapalı"
    else -> Status.Error to "Hata"
}

@Composable
private fun rememberVersion(app: App): String {
    val pkg = LocalContext.current.packageManager
    return remember { runCatching { pkg.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "dev" }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    SectionHeader(label, modifier = Modifier.padding(top = Space.md, start = 4.dp))
    PocketCard(contentPadding = PaddingValues(vertical = 4.dp), content = content)
    Spacer(Modifier.height(Space.sm))
}

@Composable
private fun NavRow(
    icon: ImageVector,
    tone: Tone,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leading = { IconTile(icon, tone = tone) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Tok.text3, modifier = Modifier.size(20.dp))
            }
        },
    )
}

// ── Detay sayfası iskeleti ──────────────────────────────────────────────────

@Composable
private fun DetailPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PocketTopBar(title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Column(Modifier.widthIn(max = 640.dp).align(Alignment.CenterHorizontally)) {
                Spacer(Modifier.height(Space.sm))
                content()
                Spacer(Modifier.height(Space.x3))
            }
        }
    }
}

@Composable
private fun CardTitle(text: String, body: String? = null) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Tok.text)
    if (body != null) {
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Tok.text2)
    }
    Spacer(Modifier.height(Space.lg))
}

// ── Görünüm ─────────────────────────────────────────────────────────────────

@Composable
private fun AppearanceContent(settings: SettingsViewModel) {
    val house = ConsoleThemes.filter { it.id.startsWith("pocket") }
    val others = ConsoleThemes.filter { !it.id.startsWith("pocket") }
    SectionHeader("Pocket temaları", modifier = Modifier.padding(start = 4.dp))
    ThemeGrid(house, settings)
    SectionHeader("Terminal temaları", modifier = Modifier.padding(top = Space.md, start = 4.dp))
    ThemeGrid(others, settings)

    SectionHeader("Terminal yazı tipi", modifier = Modifier.padding(top = Space.md, start = 4.dp))
    PocketCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        ConsoleFonts.forEachIndexed { i, f ->
            if (i > 0) InsetDivider(Space.lg + 24.dp + Space.md)
            val selected = settings.theme.fontId == f.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { settings.setFontId(f.id) })
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) Tok.primary else Tok.text3,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodyLarge, color = Tok.text)
                    Text("~/src $ git status -sb", fontFamily = f.family, fontSize = 13.sp, color = Tok.text2, maxLines = 1)
                }
            }
        }
    }

    SectionHeader("Yazı ölçeği", modifier = Modifier.padding(top = Space.md, start = 4.dp), count = "%.1f×".format(settings.theme.fontScale))
    PocketCard {
        Text(
            "user@host:~$ ls -la",
            fontFamily = consoleFont(settings.theme.fontId).family,
            fontSize = (13 * settings.theme.fontScale).sp,
            color = Tok.text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Slider(
            value = settings.theme.fontScale,
            onValueChange = { settings.setFontScale(it) },
            valueRange = 0.8f..2.0f,
            colors = SliderDefaults.colors(thumbColor = Tok.primary, activeTrackColor = Tok.primary, inactiveTrackColor = Tok.raised),
        )
        Text("Terminalde iki parmakla yakınlaştırarak da değiştirebilirsin.", style = MaterialTheme.typography.bodySmall, color = Tok.text2)
    }
}

@Composable
private fun ThemeGrid(themes: List<ConsoleTheme>, settings: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        themes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { th ->
                    ThemeCard(th, settings.theme.themeId == th.id, Modifier.weight(1f)) { settings.setThemeId(th.id) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// Tema kartı: temanın kendi renkleriyle küçük bir terminal — istem, iki
// çıktı satırı, durum renkleri. Seçili kart birincil çerçeve + tik taşır.
@Composable
private fun ThemeCard(th: ConsoleTheme, selected: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val t = Tok
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color(th.term.background))
            .border(if (selected) 2.dp else 1.dp, if (selected) t.primary else t.line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onTap)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(th.accent, th.warning, th.error).forEach { c ->
                Box(Modifier.padding(end = 4.dp).size(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(c)))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(18.dp)) {
                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "Seçili", tint = t.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("❯ make test", color = Color(th.accent), fontFamily = Code, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
        Text("ok  host/ssh  0.4s", color = Color(th.term.ansi[2]), fontFamily = Code, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
        Text("~/src/pocket", color = Color(th.term.foreground).copy(alpha = 0.7f), fontFamily = Code, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Text(
            th.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = Color(th.term.foreground),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Terminal ve oturum ──────────────────────────────────────────────────────

@Composable
private fun SessionContent(settings: SettingsViewModel) {
    PocketCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        SwitchRow(
            "Açılışta son oturuma bağlan",
            checked = settings.autoReconnect,
            onToggle = { settings.toggleAutoReconnect() },
            subtitle = "Parola ya da anahtar bu cihazda saklıysa uygulama açılır açılmaz bağlanır.",
        )
        InsetDivider(Space.lg)
        SwitchRow(
            "Kopunca otomatik yeniden bağlan",
            checked = settings.autoReconnectOnDrop,
            onToggle = { settings.toggleAutoReconnectOnDrop() },
            subtitle = "5 denemeye kadar artan aralıklarla. Kimlik ya da host anahtarı hatasında durur.",
        )
    }
    Spacer(Modifier.height(Space.md))
    PocketCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        SwitchRow(
            "Terminalde ekranı açık tut",
            checked = settings.keepScreenOn,
            onToggle = { settings.toggleKeepScreenOn() },
            subtitle = "Agent çalışırken ekran kararmaz. Yalnız terminal görünürken geçerli.",
        )
        InsetDivider(Space.lg)
        SwitchRow(
            "Tuş titreşimi",
            checked = settings.keyHaptics,
            onToggle = { settings.toggleKeyHaptics() },
            subtitle = "Tuş paneline her dokunuşta kısa titreşim.",
        )
    }

    SectionHeader("Tuş paneli kısayolları", modifier = Modifier.padding(top = Space.lg, start = 4.dp))
    Text(
        "Sık komutlar terminalin tuş paneline eklenir. Dokununca komut yazılır; Enter'a sen basarsın.",
        style = MaterialTheme.typography.bodyMedium,
        color = Tok.text2,
        modifier = Modifier.padding(start = 4.dp, bottom = Space.md),
    )
    val snippets = settings.snippetList()
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }
    if (snippets.isNotEmpty()) {
        PocketCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            snippets.forEachIndexed { i, (label, cmd) ->
                if (i > 0) InsetDivider(Space.lg)
                ListRow(
                    title = label,
                    subtitle = cmd,
                    subtitleMono = true,
                    contentPadding = PaddingValues(start = Space.lg, end = 4.dp, top = Space.sm, bottom = Space.sm),
                    trailing = { IconAction(Icons.Rounded.Delete, "$label kısayolunu sil", { removing = label to cmd }) },
                )
            }
        }
        Spacer(Modifier.height(Space.md))
    }
    TonalButton("Kısayol ekle", onClick = { adding = true }, icon = Icons.Rounded.Add)

    fun save(list: List<Pair<String, String>>) = settings.updateSnippets(list.joinToString("\n") { "${it.first}=${it.second}" })

    if (adding) {
        var label by remember { mutableStateOf("") }
        var cmd by remember { mutableStateOf("") }
        val labelOk = label.isNotBlank() && !label.contains('=') && snippets.none { it.first == label.trim() }
        PocketDialog(
            onDismiss = { adding = false },
            title = "Yeni kısayol",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    PocketTextField(
                        label, { label = it.replace("\n", "") },
                        label = "Etiket", placeholder = "gs",
                        helper = "Panelde görünen kısa ad.",
                        error = if (label.contains('=')) "Etikette = kullanılamaz." else if (snippets.any { it.first == label.trim() }) "Bu etiket zaten var." else null,
                    )
                    PocketTextField(
                        cmd, { cmd = it.replace("\n", "") },
                        label = "Komut", placeholder = "git status", mono = true,
                    )
                }
            },
            confirm = {
                GhostButton("Ekle", enabled = labelOk && cmd.isNotBlank(), onClick = {
                    save(snippets + (label.trim() to cmd))
                    adding = false
                })
            },
            dismiss = { GhostButton("Vazgeç", onClick = { adding = false }, color = Tok.text2) },
        )
    }
    removing?.let { s ->
        ConfirmDialog(
            title = "${s.first} silinsin mi?",
            message = "Kısayol tuş panelinden kaldırılır.",
            confirmLabel = "Sil",
            onConfirm = { save(snippets - s); removing = null },
            onDismiss = { removing = null },
        )
    }
}

// ── Backend ─────────────────────────────────────────────────────────────────

@Composable
private fun BackendContent(settings: SettingsViewModel, app: App) {
    var url by remember { mutableStateOf(settings.backendUrl) }
    var tenant by remember { mutableStateOf(settings.tenantToken) }
    var showTenant by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    val syncStatus by app.eventSync.status.collectAsState()
    val t = Tok

    fun save() {
        val u = url.trim()
        if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
            urlError = "Adres http:// ya da https:// ile başlamalı."
            return
        }
        urlError = null
        settings.setBackend(u, tenant)
        app.configureBackend(u, tenant.trim())
        testResult = null
        notify("Backend ayarları kaydedildi")
    }

    PocketCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Durum", style = MaterialTheme.typography.titleMedium, color = t.text, modifier = Modifier.weight(1f))
            val (s, l) = if (settings.backendConfigured) syncLabel(syncStatus) else Status.Idle to "Ayarlanmadı"
            StatusChip(s, l)
        }
        if (settings.backendConfigured && syncLabel(syncStatus).first == Status.Error) {
            Spacer(Modifier.height(Space.sm))
            Text(syncStatus, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code), color = t.danger.text)
        }
        Spacer(Modifier.height(Space.md))
        Text(
            "Backend yalnız kısa agent olay özetleri ve onay kayıtları tutar (≤256 karakter, 24 saat). Terminal, dosya ve diff içerikleri hiç gitmez.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.text2,
        )
        if (settings.backendConfigured) {
            Spacer(Modifier.height(Space.sm))
            GhostButton("Şimdi senkronla", onClick = { app.eventSync.syncNow() }, icon = Icons.Rounded.Sync)
        }
    }
    Spacer(Modifier.height(Space.lg))
    PocketCard {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            PocketTextField(
                url, { url = it; urlError = null },
                label = "Backend adresi",
                placeholder = "https://agent.ornek.com",
                mono = true,
                error = urlError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            )
            PocketTextField(
                tenant, { tenant = it },
                label = "Tenant token",
                mono = true,
                helper = "Eşleme QR'ı bunu otomatik doldurur.",
                visualTransformation = if (showTenant) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconAction(
                        if (showTenant) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        if (showTenant) "Token'ı gizle" else "Token'ı göster",
                        { showTenant = !showTenant },
                    )
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Kaydet", onClick = ::save)
                OutlineButton(
                    if (testing) "Sınanıyor…" else "Bağlantıyı test et",
                    enabled = settings.backendConfigured && !testing,
                    onClick = {
                        testing = true
                        scope.launch(Dispatchers.IO) {
                            testResult = app.backendClient?.health() == true
                            testing = false
                        }
                    },
                )
            }
            if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primary)
            testResult?.let { ok ->
                Banner(if (ok) Status.Live else Status.Error, if (ok) "Backend erişilebilir" else "Backend'e ulaşılamadı")
            }
        }
    }
}

// ── Bilinen host anahtarları ────────────────────────────────────────────────

@Composable
private fun SecurityContent(hostKeys: TofuHostKeyStore) {
    var pinnedKeys by remember { mutableStateOf(hostKeys.all()) }
    var forgetting by remember { mutableStateOf<dev.pocketagent.transport.PresentedKey?>(null) }
    val t = Tok
    Text(
        "İlk bağlantıda onayladığın parmak izleri. Pinli anahtar değişirse bağlantı durur — araya girme saldırısına karşı koruma.",
        style = MaterialTheme.typography.bodyMedium,
        color = t.text2,
        modifier = Modifier.padding(start = 4.dp, bottom = Space.lg),
    )
    if (pinnedKeys.isEmpty()) {
        PocketCard {
            Text("Henüz pinlenmiş anahtar yok. İlk bağlantıda parmak izi sorulur.", style = MaterialTheme.typography.bodyMedium, color = t.text2)
        }
    } else {
        PocketCard(contentPadding = PaddingValues(vertical = 4.dp)) {
            pinnedKeys.forEachIndexed { i, k ->
                if (i > 0) InsetDivider(Space.lg)
                Row(Modifier.padding(start = Space.lg, end = 4.dp, top = Space.md, bottom = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${k.host}:${k.port}", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Code, fontSize = 15.sp), color = t.text)
                        Spacer(Modifier.height(2.dp))
                        Text(k.algorithm, style = MaterialTheme.typography.labelMedium, color = t.text2)
                        Text(k.fingerprint, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code), color = t.text2)
                    }
                    GhostButton("Unut", onClick = { forgetting = k }, color = t.danger.text)
                }
            }
        }
    }
    forgetting?.let { k ->
        ConfirmDialog(
            title = "Anahtar unutulsun mu?",
            message = "${k.host}:${k.port} için pin kaldırılır. Sonraki bağlantıda parmak izi yeniden sorulur.",
            confirmLabel = "Unut",
            onConfirm = {
                hostKeys.forget(k.host, k.port)
                pinnedKeys = hostKeys.all()
                forgetting = null
            },
            onDismiss = { forgetting = null },
        )
    }
}

// ── Yedekleme ve kullanım ───────────────────────────────────────────────────

@Composable
private fun DataContent(settings: SettingsViewModel, usage: UsageViewModel, app: App) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val conns by app.connections.items.collectAsState()
    val t = Tok

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
                        keepScreenOn = settings.keepScreenOn,
                        keyHaptics = settings.keyHaptics,
                    ),
                )
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }.onSuccess { msg = true to "Yedek yazıldı (${conns.size} bağlantı)." }
                .onFailure { msg = false to "Yedek yazılamadı: ${it.message}" }
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
            }.onSuccess { msg = true to "${it.connections.size} bağlantı içe aktarıldı" + (if (it.skipped > 0) " (${it.skipped} atlandı)." else ".") }
                .onFailure { msg = false to "İçe aktarma başarısız: ${it.message}" }
        }
    }

    PocketCard {
        CardTitle("Yedekleme", "Bağlantılar ve ayarlar JSON olarak dışa aktarılır. Parolalar ve anahtarlar hiçbir zaman yedeğe girmez.")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            PrimaryButton("Dışa aktar", onClick = { exporter.launch("pocket-agent-backup.json") })
            OutlineButton("İçe aktar", onClick = { importer.launch(arrayOf("application/json", "text/*", "*/*")) })
        }
        msg?.let { (ok, m) ->
            Spacer(Modifier.height(Space.md))
            Banner(if (ok) Status.Live else Status.Error, m)
        }
    }
    Spacer(Modifier.height(Space.lg))
    PocketCard {
        CardTitle("Kullanım")
        if (usage.rows.isEmpty()) {
            Text(
                "Henüz kullanım verisi yok. Host raporlaması bağlandığında agent kotaları burada görünür; backend ayarlıysa her senkronda güncellenir.",
                style = MaterialTheme.typography.bodyMedium,
                color = t.text2,
            )
        } else {
            usage.rows.forEachIndexed { i, u ->
                if (i > 0) Spacer(Modifier.height(Space.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.agent, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = t.text)
                    Text(
                        "%${u.percent}" + if (u.resetIn.isNotBlank()) " · ${u.resetIn} sonra sıfırlanır" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (u.percent >= 85) t.warn.text else t.text2,
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Meter(u.percent / 100f)
            }
        }
        Spacer(Modifier.height(Space.md))
        Text("Özetler 24 saat saklanır; backend tam transcript görmez.", style = MaterialTheme.typography.bodySmall, color = t.text2)
    }
}

// ── Hakkında ────────────────────────────────────────────────────────────────

@Composable
private fun AboutContent(app: App) {
    val version = rememberVersion(app)
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(size = 72.dp)
        Spacer(Modifier.height(Space.lg))
        Text("Pocket Agent", style = MaterialTheme.typography.headlineSmall, color = Tok.text)
        Spacer(Modifier.height(4.dp))
        Text("Sürüm $version · kendi sunucun için cep terminali", style = MaterialTheme.typography.bodyMedium, color = Tok.text2)
    }
    UpdateCard(version)
    SectionHeader("Künye", modifier = Modifier.padding(top = Space.lg, start = 4.dp))
    PocketCard(contentPadding = PaddingValues(vertical = 4.dp)) {
        AboutRow("Lisans", "GPL-3.0-or-later")
        AboutRow("Yazı tipleri", "IBM Plex Sans · IBM Plex Mono · JetBrains Mono · Space Mono (OFL-1.1)")
        AboutRow("Gizlilik", "Terminal, diff ve dosyalar backend'e gitmez; yalnız ≤256 karakter özet, 24 saat")
        AboutRow("Dikte", "Cihaz içi varsayılan · kendi anahtarınla isteğe bağlı")
        AboutRow("Deep link", "pocketagent://tmux · pocketagent://add", last = true)
    }
}

@Composable
private fun AboutRow(key: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.Top) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = Tok.text2, modifier = Modifier.width(104.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Tok.text, modifier = Modifier.weight(1f))
    }
    if (!last) InsetDivider(Space.lg)
}

// Güncelleme kartı: mevcut sürüm + kontrol; güncelleme varsa sürüm + boyut
// + "İndir ve kur" → ilerleme → paket kurucu.
private enum class UpdPhase { Idle, Checking, Current, Available, Downloading, Ready, Failed }

@Composable
private fun UpdateCard(version: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(UpdPhase.Idle) }
    var rel by remember { mutableStateOf<dev.pocketagent.net.ReleaseInfo?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var err by remember { mutableStateOf<String?>(null) }
    val t = Tok

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

    PocketCard {
        Text("Güncelleme", style = MaterialTheme.typography.titleMedium, color = t.text)
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Yüklü: $version", style = MaterialTheme.typography.bodyLarge, color = t.text)
                val (line, color) = when (phase) {
                    UpdPhase.Idle -> "GitHub Releases üzerinden" to t.text2
                    UpdPhase.Checking -> "Kontrol ediliyor…" to t.text2
                    UpdPhase.Current -> "Güncel" to t.live.text
                    UpdPhase.Available -> (rel?.let { "Yeni: ${it.version} · %.0f MB".format(it.sizeBytes / 1e6f) } ?: "") to t.accent.text
                    UpdPhase.Downloading -> "İndiriliyor %${(progress * 100).toInt()}" to t.text2
                    UpdPhase.Ready -> (rel?.let { "${it.version} indirildi, kurulum açılıyor" } ?: "") to t.accent.text
                    UpdPhase.Failed -> (err ?: "Hata") to t.danger.text
                }
                Text(line, style = MaterialTheme.typography.bodyMedium, color = color)
            }
            when (phase) {
                UpdPhase.Checking, UpdPhase.Downloading ->
                    CircularProgressIndicator(Modifier.padding(end = Space.sm).size(22.dp), strokeWidth = 2.dp, color = t.primary)
                UpdPhase.Available, UpdPhase.Ready ->
                    PrimaryButton("İndir ve kur", onClick = { rel?.let(::downloadAndInstall) })
                else -> OutlineButton("Denetle", onClick = ::check)
            }
        }
        if (phase == UpdPhase.Downloading) {
            Spacer(Modifier.height(Space.md))
            Meter(progress, warnAt = 2f)
        }
        rel?.notes?.takeIf { it.isNotBlank() && phase == UpdPhase.Available }?.let {
            Spacer(Modifier.height(Space.md))
            Text(it.lines().take(4).joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = t.text2, maxLines = 4)
        }
    }
}
