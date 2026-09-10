// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Dns
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.TerminalTransport
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.LocalMonoFont
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(
    repo: ConnectionRepository,
    sessions: SessionManager,
    app: dev.pocketagent.android.App? = null,
    settings: SettingsViewModel? = null,
    pendingAdd: kotlinx.coroutines.flow.MutableStateFlow<SavedConnection?>? = null,
    onConnected: () -> Unit,
) {
    val items by repo.items.collectAsState()
    val sessionList by sessions.sessions.collectAsState()
    var editing by remember { mutableStateOf<SavedConnection?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showPair by remember { mutableStateOf(false) }
    var pairBusy by remember { mutableStateOf(false) }
    var pairError by remember { mutableStateOf<String?>(null) }
    // ~/.ssh/config içe aktarım önizlemesi + bilgi mesajı.
    var importPreview by remember { mutableStateOf<List<dev.pocketagent.transport.SshConfigEntry>?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard2 = androidx.compose.ui.platform.LocalClipboardManager.current

    // pocketagent://add?host=… → doldurulmuş yeni-host diyaloğu.
    if (pendingAdd != null) {
        val pending by pendingAdd.collectAsState()
        LaunchedEffect(pending) {
            pending?.let { editing = it; pendingAdd.value = null }
        }
    }

    // ssh config dosyası seçici → önizleme listesi.
    val sshConfigPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { s ->
                s.readBytes().decodeToString()
            } ?: ""
            val entries = dev.pocketagent.transport.SshConfig.parse(text)
            if (entries.isEmpty()) notice = "Dosyada host bulunamadı" else importPreview = entries
        }.onFailure { notice = "dosya okunamadı: ${it.message}" }
    }

    // P04: QR tarama (zxing-embedded ScanContract) + elle kod girişi
    val qrLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract(),
    ) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        val parsed = dev.pocketagent.net.PairingClient.parseQr(contents, settings?.backendUrl ?: "")
        if (parsed == null) {
            pairError = "QR tanınmadı — pa1| ile başlayan bir Pocket Agent kodu olmalı"
            showPair = true
        } else {
            pairClaim(parsed.first, parsed.second, app, repo, sessions, scope, onConnected,
                onBusy = { pairBusy = it }, onError = { pairError = it; showPair = true })
        }
    }
    val camPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            qrLauncher.launch(
                com.journeyapps.barcodescanner.ScanOptions().apply {
                    setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                    setPrompt("Host'taki QR kodunu okut")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                },
            )
        } else {
            pairError = "Kamera izni verilmedi — XXXX-XXXX kodunu elle girebilirsin"
            showPair = true
        }
    }
    // Arama: ad/host/kullanıcı içinde süz (büyük-küçük harf duyarsız)
    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter {
            it.name.contains(query, true) || it.host.contains(query, true) || it.user.contains(query, true)
        }
    }

    Scaffold(
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallFloatingActionButton(
                    onClick = { sshConfigPicker.launch(arrayOf("*/*")) },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "~/.ssh/config içe aktar")
                }
                SmallFloatingActionButton(
                    onClick = { pairError = null; showPair = true },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "QR ile bağlan")
                }
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Host ekle") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (items.isEmpty()) {
                // İlk çalıştırma sihirbazı: backend → host komutu → QR/kod.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    ConsoleCard {
                        CardHeader("Hızlı kurulum")
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "3 adımda bağlan. Secret'lar ya RAM'de tutulur ya da Keystore ile şifrelenir — asla plaintext diske yazılmaz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Space.md))
                        WizardStep(1, "Backend URL", "self-hosted API adresin (QR bunu taşır)") {
                            var url by remember { mutableStateOf(settings?.backendUrl ?: "") }
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("Backend URL") },
                                placeholder = { Text("https://api.ornek.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ConsoleOutlinedButton(
                                enabled = url.isNotBlank() && url != settings?.backendUrl,
                                onClick = { settings?.setBackend(url, settings.tenantToken.ifBlank { "default" }) },
                                modifier = Modifier.padding(top = 6.dp),
                            ) { Text("Kaydet", fontSize = 12.sp) }
                        }
                        WizardStep(2, "Host'ta çalıştır", "tek komut: daemon + gateway + QR") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                                    .padding(8.dp),
                            ) {
                                Text(
                                    "pocket-agent onboard",
                                    fontFamily = LocalMonoFont.current,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    clipboard2.setText(androidx.compose.ui.text.AnnotatedString("pocket-agent onboard"))
                                }) { Text("Kopyala", fontSize = 11.sp) }
                            }
                        }
                        WizardStep(3, "Eşle", "QR'ı tara ya da XXXX-XXXX kodu gir") {
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                ConsoleButton(onClick = { pairError = null; showPair = true }) { Text("QR / kod ile eşle") }
                                ConsoleOutlinedButton(onClick = { showAdd = true }) { Text("Elle ekle") }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (items.size > 3) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Host ara…") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                ) {
                    items(filtered, key = { it.id }) { c ->
                        val openSession = sessionList.firstOrNull { it.conn.id == c.id }
                        val sessionState = openSession?.controller?.state?.collectAsState()?.value
                        ConnectionCard(
                            conn = c,
                            isActive = sessionState == ConnectionState.ACTIVE,
                            hasSecret = repo.hasSavedSecret(c.id),
                            onConnect = {
                                val secret = repo.secret(c.id)
                                if (secret == null && openSession == null) {
                                    editing = c // secret sor: diyalog düzenleme modunda açılır
                                } else {
                                    sessions.open(c, secret)
                                    onConnected()
                                }
                            },
                            onEdit = { editing = c },
                            onDelete = { scope.launch { repo.delete(c.id) } },
                        )
                    }
                }
                }
            }
        }
    }

    // P04: Easy Pair diyaloğu — QR tara veya XXXX-XXXX kodu gir
    if (showPair) {
        var codeInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!pairBusy) showPair = false },
            title = { Text("QR / kod ile bağlan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Host'ta `pocket-agent pair` çalıştır. QR'ı tara ya da 8 haneli kodu gir — anahtar tek kullanımlık, 5 dakika geçerli.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { camPermission.launch(android.Manifest.permission.CAMERA) },
                        enabled = !pairBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("QR tara")
                    }
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { v ->
                            val clean = v.uppercase().filter { it.isLetterOrDigit() }.take(8)
                            codeInput = if (clean.length > 4) clean.take(4) + "-" + clean.drop(4) else clean
                        },
                        label = { Text("Kod (XXXX-XXXX)") },
                        singleLine = true,
                        enabled = !pairBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (pairBusy) {
                        Text("Bağlanıyor…", style = MaterialTheme.typography.bodySmall)
                    }
                    pairError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val backend = settings?.backendUrl ?: ""
                        val parsed = dev.pocketagent.net.PairingClient.parseQr(codeInput, backend)
                        if (parsed == null) {
                            pairError = if (backend.isBlank()) {
                                "Kod için backend gerekli — önce Ayarlar → Backend URL gir"
                            } else {
                                "Kod 8 haneli olmalı (XXXX-XXXX)"
                            }
                        } else {
                            pairClaim(parsed.first, parsed.second, app, repo, sessions, scope, onConnected,
                                onBusy = { pairBusy = it }, onError = { pairError = it })
                        }
                    },
                    enabled = !pairBusy && codeInput.length == 9,
                ) { Text("Bağlan") }
            },
            dismissButton = {
                TextButton(onClick = { showPair = false }, enabled = !pairBusy) { Text("Vazgeç") }
            },
        )
    }

    if (showAdd || editing != null) {
        ConnectionDialog(
            initial = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { conn, secret, remember ->
                scope.launch {
                    repo.upsert(conn, secret, remember)
                    showAdd = false
                    editing = null
                }
            },
        )
    }

    // ~/.ssh/config içe aktarım önizlemesi: hangi host'lar eklenecek göster.
    importPreview?.let { entries ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("${entries.size} host bulundu") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Secret'lar taşınmaz — her host ilk bağlanışında parola/anahtar sorar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entries.forEach { e ->
                        Text(
                            "${e.name}  ${e.user.ifBlank { "?" }}@${e.host}:${e.port}" +
                                (e.identityFile?.let { "  [$it]" } ?: ""),
                            fontFamily = LocalMonoFont.current,
                            fontSize = 11.sp,
                        )
                    }
                }
            },
            confirmButton = {
                ConsoleButton(onClick = {
                    scope.launch {
                        entries.forEach { e ->
                            runCatching { repo.upsert(dev.pocketagent.transport.SshConfig.toConnection(e), null) }
                        }
                        notice = "${entries.size} host içe aktarıldı"
                        importPreview = null
                    }
                }) { Text("İçe aktar") }
            },
            dismissButton = { TextButton(onClick = { importPreview = null }) { Text("Vazgeç") } },
        )
    }

    // Bilgi mesajı (import sonucu, dosya hatası)
    notice?.let {
        AlertDialog(
            onDismissRequest = { notice = null },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("Tamam") } },
            text = { Text(it) },
        )
    }
}

// İlk kurulum adımı: numaralı başlık + açıklama + içerik.
@Composable
private fun WizardStep(n: Int, title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(Space.sm))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Space.sm))
        content()
    }
}

@Composable
private fun ConnectionCard(
    conn: SavedConnection,
    isActive: Boolean,
    hasSecret: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ConsoleCard(
        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    conn.name.trim().take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conn.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (isActive) {
                        Spacer(Modifier.width(Space.sm))
                        StateDot(ConnectionState.ACTIVE, size = 7.dp)
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    "${conn.user}@${conn.host}:${conn.port}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalMonoFont.current),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Düzenle") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                conn.transportOrder.forEach { t ->
                    TagPill(t.name, active = t == TerminalTransport.SSH)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Son: ${relativeTime(conn.lastConnectedAt)}" + if (hasSecret) " · kayıtlı" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ConsoleButton(onClick = onConnect) {
                Text(if (isActive) "Terminale git" else "Bağlan")
            }
        }
    }
}

@Composable
private fun ConnectionDialog(
    initial: SavedConnection?,
    onDismiss: () -> Unit,
    onSave: (SavedConnection, Secret?, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "22") }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var authKind by remember { mutableStateOf(if (initial?.credentialRef == "ram:pem") "pem" else "password") }
    var secretText by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(false) }
    var autoTmux by remember { mutableStateOf(initial?.autoTmux ?: false) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    // Bağlantıyı sına + üretilen anahtarın public parçası diyalogda gösterilir.
    var probing by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<String?>(null) }
    var generatedPub by remember { mutableStateOf<String?>(null) }
    val dlgScope = rememberCoroutineScope()
    val dlgContext = androidx.compose.ui.platform.LocalContext.current
    val dlgClipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // PEM dosyası seçici (SAF) — içerik alana okunur, dosya referansı tutulmaz.
    val pemPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            dlgContext.contentResolver.openInputStream(uri)?.use { s ->
                secretText = s.readBytes().decodeToString()
            }
        }.onFailure { probeResult = "dosya okunamadı: ${it.message}" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial?.id.isNullOrBlank()) "Yeni host" else "Hostu düzenle") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Ad") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Host (örn. 192.168.1.10)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) },
                        label = { Text("Port") }, singleLine = true, modifier = Modifier.width(96.dp),
                    )
                    OutlinedTextField(
                        user, { user = it },
                        label = { Text("Kullanıcı") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                SegmentedControl(
                    options = listOf(Segment("password", "Parola"), Segment("pem", "PEM anahtarı")),
                    selected = authKind,
                    onSelect = { authKind = it; secretText = "" },
                )
                if (authKind == "password") {
                    OutlinedTextField(
                        secretText, { secretText = it },
                        label = { Text(if (initial?.id.isNullOrBlank()) "Parola" else "Parola (boş = değişme)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                } else {
                    OutlinedTextField(
                        secretText, { secretText = it },
                        label = { Text(if (initial?.id.isNullOrBlank()) "PEM içeriği" else "PEM (boş = değişme)") },
                        minLines = 3, maxLines = 5,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // id_ed25519 vb. dosyadan içe aktar
                        ConsoleOutlinedButton(onClick = { pemPicker.launch(arrayOf("*/*")) }) {
                            Text("Dosyadan al", fontSize = 12.sp)
                        }
                        ConsoleOutlinedButton(onClick = {
                            val g = dev.pocketagent.security.KeyGen.ed25519()
                            secretText = g.privatePem
                            generatedPub = g.publicOpenSsh
                        }) {
                            Text("Anahtar üret", fontSize = 12.sp)
                        }
                    }
                }
                // Kaydetmeden ağ + auth'u probe et (TOFU pin'ine dokunmaz).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConsoleOutlinedButton(
                        enabled = !probing && host.isNotBlank() && user.isNotBlank(),
                        onClick = {
                            val probe = SavedConnection(
                                name = name.ifBlank { "probe" }, host = host.trim(),
                                port = port.toIntOrNull() ?: 22, user = user.trim(),
                                credentialRef = "ram:test",
                            )
                            probing = true; probeResult = null
                            dlgScope.launch {
                                val sec = if (secretText.isBlank()) null
                                else if (authKind == "password") Secret.Password(secretText)
                                else Secret.PemKey(secretText)
                                probeResult = when (val r = dev.pocketagent.transport.SshProbe.run(probe, sec)) {
                                    is dev.pocketagent.transport.SshProbe.Result.Ok -> "✓ bağlantı + auth başarılı"
                                    is dev.pocketagent.transport.SshProbe.Result.AuthRejected -> "✗ ${r.detail}"
                                    is dev.pocketagent.transport.SshProbe.Result.Net -> "✗ ağ: ${r.detail}"
                                }
                                probing = false
                            }
                        },
                    ) { Text(if (probing) "Sınanıyor…" else "Bağlantıyı sına", fontSize = 12.sp) }
                    probeResult?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it, fontSize = 11.sp, fontFamily = LocalMonoFont.current,
                            color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = remember, onCheckedChange = { remember = it })
                    Text("Keystore ile şifreli sakla")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoTmux, onCheckedChange = { autoTmux = it })
                    Column {
                        Text("tmux'a otomatik bağlan")
                        Text(
                            "tmux new-session -A -s main — oturum kopmaya dayanıklı olur",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (errors.isNotEmpty()) {
                    Text(
                        "Eksik/hatalı: ${errors.joinToString()}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "İlk bağlantıda host parmak izi gösterilir ve pinlenir (TOFU).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cred = if (authKind == "password") "ram:password" else "ram:pem"
                val conn = SavedConnection(
                    name = name.trim(),
                    host = host.trim(),
                    port = port.toIntOrNull() ?: 0,
                    user = user.trim(),
                    credentialRef = cred,
                    transportOrder = listOf(TerminalTransport.SSH),
                    autoTmux = autoTmux,
                    id = initial?.id ?: "",
                    lastConnectedAt = initial?.lastConnectedAt ?: 0L,
                )
                val errs = conn.validate().toMutableList()
                val secretBlank = secretText.isBlank()
                if (initial?.id.isNullOrBlank() && secretBlank) errs += "secret"
                if (errs.isEmpty()) {
                    val secret = if (secretBlank) null
                    else if (authKind == "password") Secret.Password(secretText)
                    else Secret.PemKey(secretText)
                    onSave(conn, secret, remember)
                } else {
                    errors = errs
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )

    // Üretilen anahtarın public parçası: host'ta authorized_keys'e eklenecek.
    generatedPub?.let { pub ->
        AlertDialog(
            onDismissRequest = { generatedPub = null },
            title = { Text("Public anahtar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Bunu host'ta ~/.ssh/authorized_keys'e ekle. Private anahtar PEM alanına yazıldı — Kaydet de, sonra Bağlan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        pub,
                        fontFamily = LocalMonoFont.current,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                            .padding(8.dp),
                    )
                }
            },
            confirmButton = {
                ConsoleButton(onClick = {
                    dlgClipboard.setText(androidx.compose.ui.text.AnnotatedString(pub))
                    generatedPub = null
                }) { Text("Kopyala") }
            },
            dismissButton = { TextButton(onClick = { generatedPub = null }) { Text("Kapat") } },
        )
    }
}

// P04: backend'den claim → bağlantıyı Keystore'lu kaydet → oturum aç.
private fun pairClaim(
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
            onError(e.message ?: "pairing başarısız")
        } finally {
            onBusy(false)
        }
    }
}
