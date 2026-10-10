// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.SavedProfile
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionHandle
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.TerminalTransport
import dev.pocketagent.ui.theme.Space
import kotlinx.coroutines.launch

// ── Host düzenleyici ────────────────────────────────────────────────────────
// Tam ekran form: bağlantı, kimlik doğrulama, sına. Hatalar ilgili alanın
// altında; kaydet üst çubukta her zaman erişilebilir.

@Composable
fun HostEditor(
    initial: SavedConnection,
    onDismiss: () -> Unit,
    onSave: (SavedConnection, Secret?, Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
    existing: List<SavedConnection> = emptyList(),
) {
    val isNew = initial.id.isBlank()
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.takeIf { it > 0 }?.toString() ?: "22") }
    var user by remember { mutableStateOf(initial.user) }
    // Eşlenen hostlar (keystore) anahtarla bağlanır.
    var authKind by remember {
        mutableStateOf(if (initial.credentialRef == "ram:pem" || initial.credentialRef == "keystore") "pem" else "password")
    }
    var secretText by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var keep by remember { mutableStateOf(initial.credentialRef == "keystore") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var probing by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var generatedPub by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val t = Tok

    // PEM dosyası seçici (SAF) — içerik alana okunur, dosya referansı tutulmaz.
    val pemPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { s -> secretText = s.readBytes().decodeToString() }
        }.onFailure { probeResult = false to "Dosya okunamadı: ${it.message}" }
    }

    // Yazılanlar kaybolmasın: değişiklik varken kapatmak onay ister.
    val dirty = name != initial.name || host != initial.host || user != initial.user ||
        port != (initial.port.takeIf { it > 0 }?.toString() ?: "22") || secretText.isNotEmpty()
    val requestClose = { if (dirty) confirmDiscard = true else onDismiss() }
    // Aynı adres zaten kayıtlıysa uyar (engellemez: farklı anahtarla ikinci
    // kayıt bilinçli olabilir).
    val duplicate = existing.firstOrNull {
        it.id != initial.id && it.host.equals(host.trim(), ignoreCase = true) &&
            it.user == user.trim() && it.port == (port.toIntOrNull() ?: 22)
    }
    // Alan değişince eski "sına" sonucu artık geçerli değil.
    LaunchedEffect(host, user, port, secretText, authKind) { probeResult = null }

    fun save() {
        val p = port.toIntOrNull() ?: 0
        val errs = buildMap {
            if (name.isBlank()) put("name", "Bir ad ver — listede böyle görünecek.")
            if (host.isBlank()) put("host", "Host adresi gerekli.")
            if (p !in 1..65535) put("port", "1–65535 arası")
            if (user.isBlank()) put("user", "Kullanıcı gerekli.")
            if (isNew && secretText.isBlank()) put("secret", if (authKind == "password") "Parola gerekli." else "Anahtar gerekli.")
        }
        errors = errs
        if (errs.isNotEmpty()) return
        val conn = SavedConnection(
            name = name.trim(),
            host = host.trim(),
            port = p,
            user = user.trim(),
            credentialRef = if (authKind == "password") "ram:password" else "ram:pem",
            transportOrder = listOf(TerminalTransport.SSH),
            autoTmux = initial.autoTmux,
            id = initial.id,
            lastConnectedAt = initial.lastConnectedAt,
        )
        if (conn.validate().isNotEmpty()) {
            errors = mapOf("name" to "Kayıt doğrulanamadı: ${conn.validate().joinToString()}")
            return
        }
        val secret = if (secretText.isBlank()) null
        else if (authKind == "password") Secret.Password(secretText) else Secret.PemKey(secretText)
        onSave(conn, secret, keep)
    }

    FullScreenDialog(onDismiss = requestClose) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
        ) {
            PocketTopBar(
                title = if (isNew) "Yeni host" else "Hostu düzenle",
                onBack = requestClose,
                backIcon = Icons.Rounded.Close,
                backDesc = "Kapat",
                insetTop = false,
            ) {
                GhostButton("Kaydet", onClick = ::save)
                Spacer(Modifier.width(Space.sm))
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg),
            ) {
                Column(Modifier.widthIn(max = 640.dp).align(Alignment.CenterHorizontally)) {
                    Spacer(Modifier.height(Space.sm))
                    FormSection("Bağlantı") {
                        PocketTextField(
                            name, { name = it; errors = errors - "name" },
                            label = "Ad", placeholder = "prod-web", error = errors["name"],
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        PocketTextField(
                            host, { host = it.trim(); errors = errors - "host" },
                            label = "Host", placeholder = "192.168.1.10 ya da ornek.com", mono = true, error = errors["host"],
                            helper = duplicate?.let { "Bu adres zaten kayıtlı: ${it.name}" },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                            PocketTextField(
                                user, { user = it.trim(); errors = errors - "user" },
                                label = "Kullanıcı", placeholder = "root", mono = true, error = errors["user"],
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Next,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            PocketTextField(
                                port, { port = it.filter(Char::isDigit).take(5); errors = errors - "port" },
                                label = "Port", mono = true, error = errors["port"],
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.width(104.dp),
                            )
                        }
                    }
                    FormSection("Kimlik doğrulama") {
                        SegmentedTabs(
                            options = listOf(Segment("password", "Parola"), Segment("pem", "SSH anahtarı")),
                            selected = authKind,
                            onSelect = { authKind = it; secretText = ""; errors = errors - "secret" },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (authKind == "password") {
                            PocketTextField(
                                secretText, { secretText = it; errors = errors - "secret" },
                                label = "Parola",
                                helper = if (isNew) null else "Boş bırakırsan kayıtlı parola korunur.",
                                error = errors["secret"],
                                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { save() }),
                                trailing = {
                                    IconAction(
                                        if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        if (showSecret) "Parolayı gizle" else "Parolayı göster",
                                        { showSecret = !showSecret },
                                    )
                                },
                            )
                        } else {
                            PocketTextField(
                                secretText, { secretText = it; errors = errors - "secret" },
                                label = "Özel anahtar (PEM)",
                                placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----",
                                helper = if (isNew) "ed25519 ya da RSA. Anahtar cihazdan çıkmaz." else "Boş bırakırsan kayıtlı anahtar korunur.",
                                error = errors["secret"],
                                mono = true, singleLine = false, minLines = 4, maxLines = 8,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                OutlineButton("Dosyadan al", onClick = { pemPicker.launch(arrayOf("*/*")) }, icon = Icons.Rounded.FileOpen)
                                OutlineButton("Anahtar üret", onClick = {
                                    val g = dev.pocketagent.security.KeyGen.ed25519()
                                    secretText = g.privatePem
                                    generatedPub = g.publicOpenSsh
                                }, icon = Icons.Rounded.Key)
                            }
                        }
                    }
                    PocketCard(contentPadding = PaddingValues(0.dp)) {
                        SwitchRow(
                            "Bu cihazda sakla",
                            checked = keep,
                            onToggle = { keep = !keep },
                            subtitle = "Keystore ile şifrelenir. Kapalıysa yalnız uygulama açıkken bellekte kalır.",
                            modifier = Modifier.padding(vertical = Space.xs),
                        )
                    }
                    Spacer(Modifier.height(Space.lg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TonalButton(
                            if (probing) "Sınanıyor…" else "Bağlantıyı sına",
                            enabled = !probing && host.isNotBlank() && user.isNotBlank(),
                            onClick = {
                                val probe = SavedConnection(
                                    name = name.ifBlank { "probe" }, host = host.trim(),
                                    port = port.toIntOrNull() ?: 22, user = user.trim(),
                                    credentialRef = "ram:test",
                                )
                                probing = true
                                probeResult = null
                                scope.launch {
                                    val sec = if (secretText.isBlank()) null
                                    else if (authKind == "password") Secret.Password(secretText) else Secret.PemKey(secretText)
                                    probeResult = when (val r = dev.pocketagent.transport.SshProbe.run(probe, sec)) {
                                        is dev.pocketagent.transport.SshProbe.Result.Ok -> true to "Bağlantı ve kimlik doğrulama başarılı."
                                        is dev.pocketagent.transport.SshProbe.Result.AuthRejected -> false to "Kimlik reddedildi: ${r.detail}"
                                        is dev.pocketagent.transport.SshProbe.Result.Net -> false to "Ağ hatası: ${r.detail}"
                                    }
                                    probing = false
                                }
                            },
                        )
                        if (probing) {
                            Spacer(Modifier.width(Space.md))
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = t.primary)
                        }
                    }
                    probeResult?.let { (ok, msg) ->
                        Spacer(Modifier.height(Space.md))
                        Banner(if (ok) Status.Live else Status.Error, msg)
                    }
                    Spacer(Modifier.height(Space.lg))
                    Text(
                        "İlk bağlantıda host'un parmak izi gösterilir; onaylarsan pinlenir ve sonradan değişirse bağlantı durur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.text2,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    if (onDelete != null) {
                        Spacer(Modifier.height(Space.xl))
                        GhostButton("Hostu sil", onClick = onDelete, color = t.danger.text, icon = Icons.Rounded.Delete)
                    }
                    Spacer(Modifier.height(Space.x3))
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Değişiklikler kaydedilmedi",
            message = "Çıkarsan bu formda yazdıkların silinir.",
            confirmLabel = "Çık",
            onConfirm = { confirmDiscard = false; onDismiss() },
            onDismiss = { confirmDiscard = false },
        )
    }

    // Üretilen anahtarın public parçası: host'ta authorized_keys'e eklenecek.
    generatedPub?.let { pub ->
        PocketDialog(
            onDismiss = { generatedPub = null },
            title = "Public anahtar",
            icon = Icons.Rounded.Key,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text(
                        "Bunu host'ta ~/.ssh/authorized_keys dosyasına ekle. Özel anahtar forma yazıldı; kaydettikten sonra bağlanabilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ValueBox(pub)
                }
            },
            confirm = {
                GhostButton("Kopyala", icon = Icons.Rounded.ContentCopy, onClick = {
                    clipboard.setText(AnnotatedString(pub))
                    generatedPub = null
                })
            },
            dismiss = { GhostButton("Kapat", onClick = { generatedPub = null }, color = t.text2) },
        )
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = Tok.text,
        modifier = Modifier.padding(start = 4.dp, top = Space.sm, bottom = Space.md),
    )
    PocketCard {
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) { content() }
    }
    Spacer(Modifier.height(Space.lg))
}

// ── Eşleme ──────────────────────────────────────────────────────────────────
// QR birincil yol; kod girişi ikincil. Backend adresi yoksa kod için sorulur.

@Composable
fun PairingSheet(
    backendUrl: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onCode: (backend: String, code: String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf(backendUrl) }
    val needsBackend = backendUrl.isBlank()
    val t = Tok
    PocketSheet(
        onDismiss = onDismiss,
        title = "Telefonu eşle",
        subtitle = "Host'ta pocket-agent pair çalıştır. Kod tek kullanımlık ve 5 dakika geçerli.",
    ) {
        Column(Modifier.padding(horizontal = Space.xxl), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            PrimaryButton("QR kodu tara", onClick = onScan, enabled = !busy, icon = Icons.Rounded.QrCodeScanner, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(t.lineStrong))
                Text("ya da kodu gir", style = MaterialTheme.typography.labelMedium, color = t.text2, modifier = Modifier.padding(horizontal = Space.md))
                Box(Modifier.weight(1f).height(1.dp).background(t.lineStrong))
            }
            if (needsBackend) {
                PocketTextField(
                    backend, { backend = it.trim() },
                    label = "Backend adresi",
                    placeholder = "https://agent.ornek.com",
                    helper = "Kod bu sunucu üzerinden doğrulanır.",
                    mono = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
            }
            PocketTextField(
                code,
                { v ->
                    // QR içeriği ("pa1|…") yapıştırıldıysa doğrudan eşle.
                    val raw = v.trim()
                    if (raw.startsWith("pa1|")) {
                        code = ""
                        onCode(backend.ifBlank { backendUrl }, raw)
                    } else {
                        val clean = v.uppercase().filter { it.isLetterOrDigit() }.take(8)
                        code = if (clean.length > 4) clean.take(4) + "-" + clean.drop(4) else clean
                    }
                },
                label = "Eşleme kodu",
                placeholder = "XXXX-XXXX",
                mono = true,
                enabled = !busy,
                error = error,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primary)
                    Spacer(Modifier.width(Space.md))
                    Text("Eşleniyor…", style = MaterialTheme.typography.bodyMedium, color = t.text2)
                }
            }
            TonalButton(
                "Kodla bağlan",
                onClick = { onCode(backend.ifBlank { backendUrl }, code) },
                enabled = !busy && code.length == 9,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// P04: backend'den claim → bağlantıyı Keystore'lu kaydet → oturum aç.
internal fun pairClaim(
    backend: String,
    code: String,
    app: dev.pocketagent.android.App?,
    repo: ConnectionRepository,
    sessions: SessionManager,
    scope: kotlinx.coroutines.CoroutineScope,
    onConnected: () -> Unit,
    onBusy: (Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    val deviceId = app?.deviceId ?: "device:unknown"
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        onBusy(true)
        try {
            val r = dev.pocketagent.net.PairingClient.claim(backend, code, deviceId)
            val conn = SavedConnection(
                name = "${r.sshUser}@${r.sshHost}",
                host = r.sshHost,
                port = r.sshPort,
                user = r.sshUser,
                credentialRef = "keystore",
            )
            val secret = Secret.PemKey(r.privateKeyPem)
            val id = repo.upsert(conn, secret, remember = true)
            sessions.open(conn.copy(id = id), secret)
            launch(kotlinx.coroutines.Dispatchers.Main) { onConnected() }
        } catch (e: dev.pocketagent.net.PairingClient.FailureException) {
            onError(e.failure.msg)
        } catch (e: Exception) {
            onError(e.message ?: "Eşleme başarısız")
        } finally {
            onBusy(false)
        }
    }
}

// ── ~/.ssh/config içe aktarma ───────────────────────────────────────────────

@Composable
fun ImportSheet(
    entries: List<dev.pocketagent.transport.SshConfigEntry>,
    onDismiss: () -> Unit,
    onImport: (List<dev.pocketagent.transport.SshConfigEntry>) -> Unit,
) {
    val chosen = remember { mutableStateListOf<dev.pocketagent.transport.SshConfigEntry>().apply { addAll(entries) } }
    val t = Tok
    PocketSheet(
        onDismiss = onDismiss,
        title = "${entries.size} host bulundu",
        subtitle = "Parola ve anahtarlar taşınmaz; her host ilk bağlantıda sorar.",
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            entries.forEach { e ->
                val on = e in chosen
                ListRow(
                    title = e.name,
                    subtitle = "${e.user.ifBlank { "?" }}@${e.host}:${e.port}" + (e.identityFile?.let { "  $it" } ?: ""),
                    subtitleMono = true,
                    onClick = { if (on) chosen.remove(e) else chosen.add(e) },
                    contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                    leading = {
                        Checkbox(
                            checked = on,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = t.primary, checkmarkColor = t.onPrimary, uncheckedColor = t.lineStrong),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(Space.md))
        PrimaryButton(
            "İçe aktar (${chosen.size})",
            onClick = { onImport(entries.filter { it in chosen }) },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.padding(horizontal = Space.xxl).fillMaxWidth(),
        )
    }
}

// ── Profil ──────────────────────────────────────────────────────────────────

@Composable
fun ProfileEditor(
    initial: SavedProfile,
    conns: List<SavedConnection>,
    onDismiss: () -> Unit,
    onSave: (SavedProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var command by remember { mutableStateOf(initial.command) }
    var connId by remember { mutableStateOf(initial.connectionId) }
    var hostMenu by remember { mutableStateOf(false) }
    val bound = conns.firstOrNull { it.id == connId }
    val t = Tok
    PocketDialog(
        onDismiss = onDismiss,
        title = if (initial.id.isBlank()) "Yeni profil" else "Profili düzenle",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                PocketTextField(name, { name = it }, label = "Ad", placeholder = "Claude")
                PocketTextField(
                    command, { command = it },
                    label = "Komut", placeholder = "claude", mono = true,
                    helper = "Oturum açılınca otomatik çalışır.",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                )
                Column {
                    Text("Host", style = MaterialTheme.typography.labelLarge, color = t.text2)
                    Spacer(Modifier.height(6.dp))
                    Box {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(dev.pocketagent.ui.theme.FieldShape)
                                .background(t.raised)
                                .tap({ hostMenu = true })
                                .heightIn(min = 52.dp)
                                .padding(horizontal = Space.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                bound?.let { "${it.name} · ${it.user}@${it.host}" } ?: "Her seferinde sor",
                                style = MaterialTheme.typography.bodyLarge,
                                color = t.text,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = t.text2)
                        }
                        DropdownMenu(expanded = hostMenu, onDismissRequest = { hostMenu = false }, containerColor = t.raised) {
                            DropdownMenuItem(text = { Text("Her seferinde sor") }, onClick = { hostMenu = false; connId = null })
                            conns.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text("${c.name} · ${c.user}@${c.host}") },
                                    trailingIcon = if (c.id == connId) {
                                        { Icon(Icons.Rounded.Check, contentDescription = "Seçili", tint = t.primary) }
                                    } else null,
                                    onClick = { hostMenu = false; connId = c.id },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirm = {
            GhostButton(
                "Kaydet",
                enabled = name.isNotBlank() && command.isNotBlank(),
                onClick = { onSave(SavedProfile(name.trim(), command.trim(), connId, initial.id)) },
            )
        },
        dismiss = { GhostButton("Vazgeç", onClick = onDismiss, color = t.text2) },
    )
}

// ── Host seçici ─────────────────────────────────────────────────────────────

@Composable
fun HostPickerSheet(
    title: String,
    subtitle: String?,
    hosts: List<SavedConnection>,
    onDismiss: () -> Unit,
    onPick: (SavedConnection) -> Unit,
) {
    PocketSheet(onDismiss = onDismiss, title = title, subtitle = subtitle) { dismiss ->
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            hosts.sortedByDescending { it.lastConnectedAt }.forEach { c ->
                ListRow(
                    title = c.name,
                    subtitle = "${c.user}@${c.host}:${c.port}",
                    subtitleMono = true,
                    contentPadding = PaddingValues(horizontal = Space.xxl, vertical = Space.md),
                    leading = { HostAvatar(c.name, c.host + c.user) },
                    onClick = { dismiss(); onPick(c) },
                )
            }
        }
    }
}

// ── Oturumu kapat ───────────────────────────────────────────────────────────
// Kapatmak host'taki tmux'u ve içindeki agent'ı da sonlandırır — bu yüzden
// her zaman sorulur ve "ayır" seçeneği sunulur: bağlantı kapanır, oturum
// host'ta çalışmaya devam eder.

@Composable
fun CloseSessionSheet(
    h: SessionHandle,
    sessions: SessionManager,
    name: String,
    tmux: String?,
    onDone: (closed: Boolean) -> Unit,
) {
    val shared = sessions.isShared(h.id)
    PocketSheet(
        onDismiss = { onDone(false) },
        title = "$name kapatılsın mı?",
        subtitle = closeSubtitle(shared, tmux),
    ) { dismiss ->
        CloseSessionActions(h, sessions, shared, onDone = { onDone(true) }, onCancel = dismiss)
    }
}

internal fun closeSubtitle(shared: Boolean, tmux: String?): String =
    if (shared) "Bu oturum başka bir cihaza ait; yalnızca bu cihazdan ayrılırsın."
    else "Host'taki tmux oturumu" + (tmux?.let { " ($it)" } ?: "") + " seçimine göre ya kapanır ya da çalışmaya devam eder."

// Kapatma seçenekleri; hem tek başına sayfada hem oturum menüsünün
// içinde (aynı sayfa onay görünümüne döner) kullanılır.
@Composable
internal fun CloseSessionActions(
    h: SessionHandle,
    sessions: SessionManager,
    shared: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!shared) {
        SheetAction(
            Icons.Rounded.Close, "Sonlandır",
            sublabel = "tmux oturumu ve içindeki süreçler (agent dahil) kapanır",
            danger = true,
            onClick = { sessions.close(h.id); onDone() },
        )
    }
    SheetAction(
        Icons.Rounded.LinkOff, if (shared) "Ayrıl" else "Ayır",
        sublabel = "Bağlantı kapanır; oturum host'ta çalışmaya devam eder",
        onClick = { sessions.close(h.id, kill = false); onDone() },
    )
    OutlineButton(
        "Vazgeç",
        onClick = onCancel,
        modifier = Modifier.padding(start = Space.xxl, end = Space.xxl, top = Space.sm).fillMaxWidth(),
    )
}
