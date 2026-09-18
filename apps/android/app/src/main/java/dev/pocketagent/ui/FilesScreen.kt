// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.pocketagent.transport.RemoteFile
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed

// P15: gerçek dosya sekmesi — aktif SSH oturumunun SFTP kanalı üzerinden
// uzak dosya sistemi gezgini. Backend içerik görmez; trafik SSH içinde kalır.
@Composable
fun FilesScreen(files: FilesViewModel, onOpenTerminal: () -> Unit = {}) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) { kotlinx.coroutines.delay(2500); notice = null }
    }

    // Uzun-basma aksiyonları + yeni klasör + yola git diyalog durumları.
    var actionFile by remember { mutableStateOf<RemoteFile?>(null) }
    var renaming by remember { mutableStateOf<RemoteFile?>(null) }
    var deleting by remember { mutableStateOf<RemoteFile?>(null) }
    var mkdirIn by remember { mutableStateOf<RemoteFile?>(null) }
    var mkdirOpen by remember { mutableStateOf(false) }
    var gotoOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Çoklu seçim (aksiyon diyaloğundaki "Seç" ile başlar) + ad filtresi +
    // içerik arama (grep) durumları.
    val selected = remember { androidx.compose.runtime.mutableStateListOf<RemoteFile>() }
    var deletingMany by remember { mutableStateOf<List<RemoteFile>?>(null) }
    var filterOpen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var grepOpen by remember { mutableStateOf(false) }

    // Oturum açıldığında home dizinine gir
    LaunchedEffect(files.hasActiveSftp()) { files.open() }

    // İndirme tamamlanınca paylaşım sayfası (tek dosya → SEND, çoklu → SEND_MULTIPLE)
    LaunchedEffect(files.downloaded) {
        val list = files.downloaded ?: return@LaunchedEffect
        if (list.isEmpty()) { files.clearDownloaded(); return@LaunchedEffect }
        val uris = list.map {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it)
        }
        val share = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        share.type = "*/*"
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(share, list.first().name))
        files.clearDownloaded()
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val (name, bytes) = pickedFile(context, uri) ?: return@rememberLauncherForActivityResult
        if (bytes.size <= 10 * 1024 * 1024) files.upload(name, bytes) // P15: 10MB cap
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (!files.hasActiveSftp()) {
            NoSessionCard()
            return@Column
        }

        // Kaynak seçimi: SFTP (tüm FS) veya Workspace (gateway jail'i)
        SegmentedControl(
            options = listOf(
                Segment(FilesMode.SFTP, "SFTP"),
                Segment(FilesMode.WORKSPACE, "Workspace"),
            ),
            selected = files.mode,
            onSelect = { files.selectMode(it) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (files.mode == FilesMode.WORKSPACE && files.gatewayAvailable == false) {
            WorkspaceMissingCard()
            return@Column
        }

        // Workspace modunda diff kısayolları + preview
        if (files.mode == FilesMode.WORKSPACE) {
            var previewPort by remember { mutableStateOf<String?>(null) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("staged" to "Staged", "unstaged" to "Unstaged", "working" to "Working", "last" to "Son commit").forEach { (kind, label) ->
                    ConsoleOutlinedButton(onClick = { files.gwDiff(kind, "git diff ($label)") }) { Text(label) }
                }
                ConsoleOutlinedButton(onClick = { previewPort = "" }) { Text("Dev server…") }
                ConsoleOutlinedButton(onClick = { files.loadTranscripts() }) { Text("Agent sohbetleri") }
            }
            // Son agent transcript'leri (allowlist dizinlerinden; ~/.claude, ~/.codex)
            files.transcripts?.let { list ->
                if (list.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        list.take(5).forEach { t ->
                            TextButton(onClick = { files.openTranscript(t) }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        t.src,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        t.rel.substringAfterLast('/').removeSuffix(".jsonl"),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        relativeTime(t.mtime * 1000),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Dev-server önizleme diyaloğu (loopback-only)
            previewPort?.let { current ->
                var port by remember { mutableStateOf(current) }
                var path by remember { mutableStateOf("/") }
                AlertDialog(
                    onDismissRequest = { previewPort = null },
                    title = { Text("Dev server önizleme") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Yalnız host üzerindeki loopback adresler (127.0.0.1) — SSRF korumalı.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                                label = { Text("Port (örn. 3000)") },
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = path,
                                onValueChange = { path = it },
                                label = { Text("Yol") },
                                singleLine = true,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            port.toIntOrNull()?.let { files.gwPreview(it, path) }
                            previewPort = null
                        }) { Text("Getir") }
                    },
                    dismissButton = { TextButton(onClick = { previewPort = null }) { Text("Vazgeç") } },
                )
            }
        }

        // Geri tuşu önce seçimi, sonra üst dizini kapatır; kökte normal davranır.
        val canGoUp = if (files.mode == FilesMode.WORKSPACE) files.gwPath.isNotEmpty()
        else files.path != null && files.path != "/"
        BackHandler(enabled = canGoUp) { files.up() }
        BackHandler(enabled = selected.isNotEmpty()) { selected.clear() }

        when {
            // Çoklu seçim çubuğu: sayı + toplu indir/kopyala/sil + kapat.
            selected.isNotEmpty() -> Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${selected.size} seçildi",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                IconButton(
                    onClick = {
                        files.downloadAll(selected.toList())
                        selected.clear()
                    },
                    enabled = selected.any { !it.isDir },
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "İndir")
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(selected.joinToString("\n") { it.path }))
                    notice = "${selected.size} yol kopyalandı"
                    selected.clear()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Yolları kopyala")
                }
                IconButton(onClick = { deletingMany = selected.toList() }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Sil",
                        tint = TermRed,
                    )
                }
                IconButton(onClick = { selected.clear() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Seçimi kapat")
                }
            }
            // Ad filtresi: geçerli dizin listesini istemci tarafında süzer.
            filterOpen -> Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Bu dizinde ara…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { filter = ""; filterOpen = false }) {
                    Icon(Icons.Filled.Close, contentDescription = "Aramayı kapat")
                }
            }
            // Yol çubuğu: dokunulabilir breadcrumb — her segment o dizine atlar.
            else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { files.up() },
                    enabled = canGoUp,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Üst dizin")
                }
                PathBar(files, Modifier.weight(1f))
                IconButton(onClick = { files.refresh() }, enabled = !files.loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Yenile")
                }
                if (files.mode == FilesMode.SFTP) {
                    IconButton(onClick = { uploadLauncher.launch("*/*") }, enabled = !files.loading) {
                        Icon(Icons.Filled.Upload, contentDescription = "Dosya yükle")
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Diğer")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Dosya ara…") },
                            onClick = { menuOpen = false; filterOpen = true },
                        )
                        if (files.mode == FilesMode.SFTP) {
                            DropdownMenuItem(
                                text = { Text("İçerikte ara…") },
                                onClick = { menuOpen = false; grepOpen = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Yola git…") },
                            onClick = { menuOpen = false; gotoOpen = true },
                        )
                        if (files.mode == FilesMode.SFTP) {
                            DropdownMenuItem(
                                text = { Text("Yeni klasör") },
                                onClick = { menuOpen = false; mkdirOpen = true },
                            )
                        }
                    }
                }
            }
        }

        files.error?.let {
            Text(
                it,
                color = TermRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        notice?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (files.loading && files.entries.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val shown = if (filter.isBlank()) files.entries
                else files.entries.filter { it.name.contains(filter, ignoreCase = true) }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.path }) { f ->
                        RemoteFileRow(
                            f,
                            selected = f in selected,
                            onClick = {
                                if (selected.isNotEmpty()) {
                                    if (f in selected) selected.remove(f) else selected.add(f)
                                } else {
                                    files.onFile(f)
                                }
                            },
                            onLongClick = { actionFile = f },
                        )
                        SoftDivider(Modifier.padding(start = 62.dp))
                    }
                }
            }
            if (files.downloading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        Text(
            (if (files.mode == FilesMode.WORKSPACE) "Gateway • SSH tüneli içinde • workspace jail"
            else "SFTP • SSH oturumu içinde • indirme ≤10MB") +
                " • ${files.entries.size} öğe",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }

    // P14: agent transcript sohbet görünümü
    files.chatBlocks?.let { (name, blocks) ->
        ChatDialog(title = name, blocks = blocks, onClose = { files.dismissChat() })
    }

    // Önizleme diyaloğu (diff içeriği renklendirilir)
    files.preview?.let { (name, content) ->
        AlertDialog(
            onDismissRequest = { files.dismissPreview() },
            confirmButton = { TextButton(onClick = { files.dismissPreview() }) { Text("Kapat") } },
            title = { Text(name, fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (name.startsWith("git diff")) {
                        Text(
                            diffAnnotated(content),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        )
                    } else {
                        Text(
                            content,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            },
        )
    }

    // ── Uzun-basma aksiyon diyaloğu ─────────────────────────────────────────
    actionFile?.let { f ->
        AlertDialog(
            onDismissRequest = { actionFile = null },
            title = { Text(f.name, fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        f.path,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionRow("Yolu kopyala") {
                        clipboard.setText(AnnotatedString(f.path))
                        notice = "yol kopyalandı"
                        actionFile = null
                    }
                    ActionRow("Terminale yaz") {
                        if (files.pastePathToTerminal(f)) {
                            actionFile = null
                            onOpenTerminal()
                        } else {
                            notice = "aktif oturum yok"
                            actionFile = null
                        }
                    }
                    ActionRow("Seç") {
                        if (f !in selected) selected.add(f)
                        actionFile = null
                    }
                    if (files.mode == FilesMode.SFTP) {
                        if (!f.isDir) {
                            ActionRow("İndir / paylaş") {
                                files.download(f)
                                actionFile = null
                            }
                        } else {
                            ActionRow("İçinde yeni klasör") {
                                mkdirIn = f
                                actionFile = null
                            }
                        }
                        ActionRow("Yeniden adlandır") {
                            renaming = f
                            actionFile = null
                        }
                        ActionRow("Sil", danger = true) {
                            deleting = f
                            actionFile = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionFile = null }) { Text("Kapat") } },
        )
    }

    // Yeniden adlandır
    renaming?.let { f ->
        var name by remember { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Yeniden adlandır") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Yeni ad") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { files.rename(f, name); renaming = null }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Vazgeç") } },
        )
    }

    // Yeni klasör (cwd veya uzun-basma ile seçilen dizin içi)
    if (mkdirOpen || mkdirIn != null) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mkdirOpen = false; mkdirIn = null },
            title = { Text("Yeni klasör") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mkdirIn?.let {
                        Text(
                            "İçinde: ${it.path}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Klasör adı") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    files.mkdir(name, mkdirIn)
                    mkdirOpen = false; mkdirIn = null
                }) { Text("Oluştur") }
            },
            dismissButton = {
                TextButton(onClick = { mkdirOpen = false; mkdirIn = null }) { Text("Vazgeç") }
            },
        )
    }

    // Yola git (derin uzak yolları elle girmek için)
    if (gotoOpen) {
        var target by remember {
            mutableStateOf(if (files.mode == FilesMode.WORKSPACE) files.gwPath else files.path ?: "/")
        }
        AlertDialog(
            onDismissRequest = { gotoOpen = false },
            title = { Text("Yola git") },
            text = {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Dizin yolu") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = target.trim()
                    if (t.isNotEmpty()) {
                        // SFTP'de göreli yol cwd'ye bağlanır; workspace zaten
                        // jail-göreli çalışır.
                        val abs = if (files.mode == FilesMode.SFTP && !t.startsWith("/")) {
                            (files.path ?: "/").trimEnd('/') + "/" + t
                        } else t
                        files.cd(abs)
                    }
                    gotoOpen = false
                }) { Text("Git") }
            },
            dismissButton = { TextButton(onClick = { gotoOpen = false }) { Text("Vazgeç") } },
        )
    }

    // Silme onayı
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Sil") },
            text = {
                Text(
                    if (f.isDir) "'${f.name}' klasörü ve içindekiler kalıcı olarak silinsin mi?"
                    else "'${f.name}' kalıcı olarak silinsin mi?",
                )
            },
            confirmButton = {
                TextButton(onClick = { files.delete(f); deleting = null }) {
                    Text("Sil", color = TermRed)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }

    // Toplu silme onayı
    deletingMany?.let { fs ->
        AlertDialog(
            onDismissRequest = { deletingMany = null },
            title = { Text("Sil") },
            text = {
                Text("${fs.size} öğe kalıcı olarak silinsin mi? (klasörler içerikleriyle birlikte)")
            },
            confirmButton = {
                TextButton(onClick = {
                    files.deleteAll(fs)
                    deletingMany = null
                    selected.clear()
                }) { Text("Sil", color = TermRed) }
            },
            dismissButton = { TextButton(onClick = { deletingMany = null }) { Text("Vazgeç") } },
        )
    }

    // İçerikte ara (grep) — desen girişi
    if (grepOpen) {
        var pattern by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { grepOpen = false },
            title = { Text("İçerikte ara") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Geçerli dizinde recursive grep (.git hariç, binary atlanır).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text("Desen") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { files.grep(pattern); grepOpen = false }) { Text("Ara") }
            },
            dismissButton = { TextButton(onClick = { grepOpen = false }) { Text("Vazgeç") } },
        )
    }

    // Grep sonuçları: yol:satır → dokununca üst dizine iner + önizleme açar.
    if (files.grepRunning || files.grepResults != null) {
        AlertDialog(
            onDismissRequest = { files.dismissGrep() },
            title = { Text("İçerik arama sonuçları") },
            text = {
                if (files.grepRunning) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { CircularProgressIndicator() }
                } else {
                    val hits = files.grepResults.orEmpty()
                    if (hits.isEmpty()) {
                        Text("eşleşme yok")
                    } else {
                        LazyColumn(Modifier.height(320.dp)) {
                            items(hits, key = { "${it.path}:${it.line}:${it.text}" }) { h ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            files.revealPath(h.path)
                                            files.dismissGrep()
                                        }
                                        .padding(vertical = 6.dp),
                                ) {
                                    Text(
                                        "${h.path}:${h.line}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                    )
                                    Text(
                                        h.text.trim(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { files.dismissGrep() }) { Text("Kapat") }
            },
        )
    }
}

// Aksiyon diyaloğu satırı: sola yaslı tam-genişlik metin düğmesi.
@Composable
private fun ActionRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = if (danger) TermRed else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Basit diff renklendirme: + yeşil, - kırmızı, @@ mor, başlıklar kalın.
private fun diffAnnotated(content: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        content.lines().forEach { line ->
            val style = when {
                line.startsWith("+++") || line.startsWith("---") ->
                    androidx.compose.ui.text.SpanStyle(color = dev.pocketagent.ui.theme.ConsoleDim)
                line.startsWith("+") ->
                    androidx.compose.ui.text.SpanStyle(color = dev.pocketagent.ui.theme.TermGreen)
                line.startsWith("-") ->
                    androidx.compose.ui.text.SpanStyle(color = TermRed)
                line.startsWith("@@") ->
                    androidx.compose.ui.text.SpanStyle(color = dev.pocketagent.ui.theme.TermPurple)
                line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("commit ") ->
                    androidx.compose.ui.text.SpanStyle(
                        color = dev.pocketagent.ui.theme.TermAmber,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                else -> null
            }
            if (style != null) {
                withStyle(style) { append(line); append('\n') }
            } else {
                append(line); append('\n')
            }
        }
    }

@Composable
private fun RemoteFileRow(
    f: RemoteFile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListRow(
        title = f.name,
        titleMono = true,
        subtitle = buildString {
            if (!f.isDir) append(humanSize(f.size))
            if (f.mtime > 0) {
                if (isNotEmpty()) append(" · ")
                append(relativeTime(f.mtime * 1000))
            }
        }.ifBlank { null },
        leading = {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            f.isDir -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (f.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = if (f.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        trailing = if (selected) {
            {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else null,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

// Dokunulabilir yol çubuğu: "/a/b/c" → / a / b / c; her segment o derinliğe
// cd eder. Yol uzayınca en derin segment görünür kalır (sona otomatik kayar).
@Composable
private fun PathBar(files: FilesViewModel, modifier: Modifier = Modifier) {
    val isGw = files.mode == FilesMode.WORKSPACE
    val rel = if (isGw) files.gwPath else (files.path ?: "").trim('/')
    val segments = rel.split('/').filter { it.isNotEmpty() }
    val scroll = rememberScrollState()
    LaunchedEffect(rel) { scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "/",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = if (segments.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { files.cd(if (isGw) "" else "/") }.padding(vertical = 6.dp, horizontal = 2.dp),
        )
        segments.forEachIndexed { i, seg ->
            val last = i == segments.lastIndex
            Text(
                seg,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .clickable(enabled = !last) {
                        val target = segments.take(i + 1).joinToString("/")
                        files.cd(if (isGw) target else "/$target")
                    }
                    .padding(vertical = 6.dp),
            )
            if (!last) {
                Text("/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// SAF seçici sonucu: görünen ad + baytlar (null = okunamadı).
// FilesScreen upload ve terminal agent-ek akışı paylaşır.
internal fun pickedFile(context: android.content.Context, uri: android.net.Uri): Pair<String, ByteArray>? {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
    } ?: "upload.bin"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return name to bytes
}

// Uzak dosya adı: kabuk/agent prompt'una güvenle yazılabilsin diye
// boşluk/özel karakterler alt çizgiye çevrilir.
internal fun sanitizeRemoteName(name: String): String =
    name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "upload.bin" }

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    else -> "${"%.2f".format(bytes / 1024.0 / 1024.0 / 1024.0)} GB"
}

@Composable
private fun WorkspaceMissingCard() {
    Spacer(Modifier.height(Space.lg))
    ConsoleCard {
        CardHeader("Workspace gateway kurulu değil")
        Spacer(Modifier.height(Space.sm))
        Text(
            "Host'ta çalıştır:\n  pocket-agent gateway serve\n  veya kalıcı servis:\n  pocket-agent service install-gateway\n\n" +
                "Token ~/.config/pocket-agent/gateway.token altında üretilir; " +
                "uygulama onu SSH oturumu içinden okur. Gateway yalnız 127.0.0.1:24543 dinler.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoSessionCard() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = Icons.Filled.Folder,
            title = "Uzak dosyalar",
            body = "Aktif bir SSH oturumu yok. Terminal sekmesinden bir host'a bağlan; bu sekme aynı oturumun SFTP kanalıyla uzak dosya sistemini gösterir.",
        )
        Spacer(Modifier.height(Space.md))
        Column(Modifier.padding(horizontal = Space.xl)) {
            ConsoleCard {
                CardHeader("Güvenlik")
                Spacer(Modifier.height(Space.xs))
                Text(
                    "• Dosyalar yalnız SSH tünelinde akar, backend içerik görmez\n" +
                        "• İndirilenler paylaşım önbelleğine düşer (10MB üst sınır)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
