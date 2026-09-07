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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.TerminalTransport
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(
    repo: ConnectionRepository,
    sessions: SessionManager,
    app: dev.pocketagent.android.App? = null,
    settings: SettingsViewModel? = null,
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
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(onClick = { pairError = null; showPair = true }) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "QR ile bağlan")
                }
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Host ekle") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (items.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Dns, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Kayıtlı host yok", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "SSH erişimi olan bir host ekle. Secret'lar ya RAM'de tutulur ya da Keystore ile şifrelenir — asla plaintext diske yazılmaz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(conn.name, style = MaterialTheme.typography.titleMedium)
                        if (isActive) {
                            Spacer(Modifier.width(8.dp))
                            StateDot(ConnectionState.ACTIVE)
                        }
                    }
                    Text(
                        "${conn.user}@${conn.host}:${conn.port}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                conn.transportOrder.forEach { t ->
                    FilterChip(selected = t == TerminalTransport.SSH, onClick = {}, label = { Text(t.name) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Son: ${relativeTime(conn.lastConnectedAt)}" + if (hasSecret) " • secret kayıtlı" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = onConnect) {
                    Text(if (isActive) "Terminale git" else "Bağlan")
                }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni host" else "Hostu düzenle") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = authKind == "password",
                        onClick = { authKind = "password"; secretText = "" },
                        label = { Text("Parola") },
                    )
                    FilterChip(
                        selected = authKind == "pem",
                        onClick = { authKind = "pem"; secretText = "" },
                        label = { Text("PEM anahtarı") },
                    )
                }
                if (authKind == "password") {
                    OutlinedTextField(
                        secretText, { secretText = it },
                        label = { Text(if (initial == null) "Parola" else "Parola (boş = değişme)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                } else {
                    OutlinedTextField(
                        secretText, { secretText = it },
                        label = { Text(if (initial == null) "PEM içeriği" else "PEM (boş = değişme)") },
                        minLines = 3, maxLines = 5,
                    )
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
                if (initial == null && secretBlank) errs += "secret"
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
