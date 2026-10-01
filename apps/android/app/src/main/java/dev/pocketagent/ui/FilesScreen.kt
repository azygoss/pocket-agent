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
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder as OutlinedFolder
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dev.pocketagent.ui.theme.Readout
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.RoundedCornerShape

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

    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
        if (!files.hasActiveSftp()) {
            NoSessionCard()
            return@Column
        }

        // Başlık: ekran adı + aktif host okuması; dizin aksiyonları sağda.
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = Space.md)) {
            ScreenHeader(
                "Dosyalar",
                meta = listOfNotNull(files.hostLabel(), if (files.mode == FilesMode.WORKSPACE) "workspace" else "sftp").joinToString(" · "),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { files.refresh() }, enabled = !files.loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Yenile", tint = Tok.text2)
            }
            if (files.mode == FilesMode.SFTP) {
                IconButton(onClick = { uploadLauncher.launch("*/*") }, enabled = !files.loading) {
                    Icon(Icons.Filled.Upload, contentDescription = "Dosya yükle", tint = Tok.text2)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Diğer", tint = Tok.text2)
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
                                    TagPill(t.src)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        t.rel.substringAfterLast('/').removeSuffix(".jsonl"),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout),
                                        color = Tok.text,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        relativeTime(t.mtime * 1000),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = Readout, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                                        color = Tok.muted,
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
                PocketAlertDialog(
                    onDismissRequest = { previewPort = null },
                    title = { Text("Dev server önizleme") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Yalnız host üzerindeki loopback adresler (127.0.0.1) — SSRF korumalı.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Tok.muted,
                            )
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                                label = { Text("Port (örn. 3000)") },
                        mono = true,
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = path,
                                onValueChange = { path = it },
                                label = { Text("Yol") },
                        mono = true,
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
                    Icon(Icons.Filled.Download, contentDescription = "İndir", tint = Tok.text2)
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(selected.joinToString("\n") { it.path }))
                    notice = "${selected.size} yol kopyalandı"
                    selected.clear()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Yolları kopyala", tint = Tok.text2)
                }
                IconButton(onClick = { deletingMany = selected.toList() }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Sil",
                        tint = Tok.danger,
                    )
                }
                IconButton(onClick = { selected.clear() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Seçimi kapat", tint = Tok.text2)
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
                    Icon(Icons.Filled.Close, contentDescription = "Aramayı kapat", tint = Tok.text2)
                }
            }
            // Yol çubuğu: dokunulabilir breadcrumb — her segment o dizine atlar.
            else -> Row(Modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { files.up() },
                    enabled = canGoUp,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Üst dizin", tint = if (canGoUp) Tok.text2 else Tok.faint)
                }
                PathBar(files, Modifier.weight(1f))
            }
        }

        files.error?.let {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SignalPixel(Signal.Error, size = 7.dp)
                Spacer(Modifier.width(8.dp))
                Text(it, color = Tok.text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout))
            }
        }
        notice?.let {
            Text(
                it,
                color = Tok.muted,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (files.loading && files.entries.isEmpty()) {
                // İskelet: gerçek satır geometrisinde loş bloklar — liste geldiğinde
                // düzen kaymaz (CLS yok). Shimmer yok; tek okuma "yükleniyor".
                Column(Modifier.fillMaxSize().semantics { contentDescription = "Yükleniyor" }) {
                    repeat(7) { i -> SkeletonRow(widthFraction = listOf(0.42f, 0.6f, 0.35f, 0.5f, 0.7f, 0.3f, 0.55f)[i]) }
                }
            } else {
                val shown = if (filter.isBlank()) files.entries
                else files.entries.filter { it.name.contains(filter, ignoreCase = true) }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.path }, contentType = { if (it.isDir) 0 else 1 }) { f ->
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
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Tok.raised)
                        .border(1.dp, Tok.border, MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) { BusyPixel("indiriliyor…") }
            }
        }

        Text(
            (if (files.mode == FilesMode.WORKSPACE) "gateway · SSH tüneli · workspace jail"
            else "sftp · SSH oturumu içinde · indirme ≤10MB") +
                " · ${files.entries.size} öğe",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = Readout, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
            color = Tok.muted,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }

    // P14: agent transcript sohbet görünümü
    files.chatBlocks?.let { (name, blocks) ->
        ChatDialog(title = name, blocks = blocks, onClose = { files.dismissChat() })
    }

    // Önizleme diyaloğu (diff içeriği renklendirilir)
    files.preview?.let { (name, content) ->
        PocketAlertDialog(
            onDismissRequest = { files.dismissPreview() },
            confirmButton = { TextButton(onClick = { files.dismissPreview() }) { Text("Kapat") } },
            title = { Text(name, fontFamily = Readout, fontSize = 14.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (name.startsWith("git diff")) {
                        Text(
                            diffAnnotated(content, Tok),
                            fontFamily = Readout,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        )
                    } else {
                        Text(
                            content,
                            fontFamily = Readout,
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
        PocketAlertDialog(
            onDismissRequest = { actionFile = null },
            title = { Text(f.name, fontFamily = Readout, fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        f.path,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout),
                        color = Tok.muted,
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
        PocketAlertDialog(
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
        PocketAlertDialog(
            onDismissRequest = { mkdirOpen = false; mkdirIn = null },
            title = { Text("Yeni klasör") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mkdirIn?.let {
                        Text(
                            "İçinde: ${it.path}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout),
                            color = Tok.muted,
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
        PocketAlertDialog(
            onDismissRequest = { gotoOpen = false },
            title = { Text("Yola git") },
            text = {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Dizin yolu") },
                        mono = true,
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
        PocketAlertDialog(
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
                    Text("Sil", color = Tok.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }

    // Toplu silme onayı
    deletingMany?.let { fs ->
        PocketAlertDialog(
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
                }) { Text("Sil", color = Tok.danger) }
            },
            dismissButton = { TextButton(onClick = { deletingMany = null }) { Text("Vazgeç") } },
        )
    }

    // İçerikte ara (grep) — desen girişi
    if (grepOpen) {
        var pattern by remember { mutableStateOf("") }
        PocketAlertDialog(
            onDismissRequest = { grepOpen = false },
            title = { Text("İçerikte ara") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Geçerli dizinde recursive grep (.git hariç, binary atlanır).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tok.muted,
                    )
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text("Desen") },
                        mono = true,
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
        PocketAlertDialog(
            onDismissRequest = { files.dismissGrep() },
            title = { Text("İçerik arama sonuçları") },
            text = {
                if (files.grepRunning) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { BusyPixel("aranıyor…") }
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
                                            fontFamily = Readout,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                    )
                                    Text(
                                        h.text.trim(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = Readout,
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
            color = if (danger) Tok.danger else Tok.text,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Diff renklendirme — renk sinyaldir: + success, - danger, @@ accent,
// başlıklar kalın mürekkep, +++/--- loş.
private fun diffAnnotated(content: String, t: dev.pocketagent.ui.theme.PocketTokens): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        content.lines().forEach { line ->
            val style = when {
                line.startsWith("+++") || line.startsWith("---") ->
                    androidx.compose.ui.text.SpanStyle(color = t.muted)
                line.startsWith("+") ->
                    androidx.compose.ui.text.SpanStyle(color = t.success)
                line.startsWith("-") ->
                    androidx.compose.ui.text.SpanStyle(color = t.danger)
                line.startsWith("@@") ->
                    androidx.compose.ui.text.SpanStyle(color = t.accent)
                line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("commit ") ->
                    androidx.compose.ui.text.SpanStyle(
                        color = t.text,
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

// Dosya satırı: türünü söyleyen çizgi ikon (klasör, kod, görsel, arşiv,
// metin), mono ad + zaman; boyut sağda hizalı okuma. Seçim zeminle + mavi tik.
@Composable
private fun RemoteFileRow(
    f: RemoteFile,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = Tok
    ListRow(
        title = f.name,
        titleMono = true,
        subtitle = if (f.mtime > 0) relativeTime(f.mtime * 1000) else null,
        subtitleMono = true,
        modifier = Modifier.background(if (selected) t.active else androidx.compose.ui.graphics.Color.Transparent),
        leading = {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (f.isDir) t.raised else t.hover),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    fileIcon(f),
                    contentDescription = null,
                    tint = if (f.isDir) t.text else t.text2,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        trailing = {
            when {
                selected -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = t.accent, modifier = Modifier.size(20.dp))
                f.isDir -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = t.faint,
                    modifier = Modifier.size(18.dp),
                )
                else -> Text(
                    humanSize(f.size),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = Readout, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = t.muted,
                )
            }
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

private fun fileIcon(f: RemoteFile): androidx.compose.ui.graphics.vector.ImageVector {
    if (f.isDir) return Icons.Outlined.OutlinedFolder
    val ext = f.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts", "java", "go", "rs", "py", "js", "ts", "tsx", "jsx", "c", "h", "cpp", "rb", "swift", "sh", "zsh", "bash", "lua", "php" -> Icons.Outlined.Code
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "heic" -> Icons.Outlined.Image
        "zip", "gz", "tgz", "tar", "xz", "bz2", "7z", "rar", "zst", "apk", "jar" -> Icons.Outlined.Inventory2
        "md", "txt", "rst", "log", "pdf", "doc", "docx" -> Icons.Outlined.Description
        "json", "yaml", "yml", "toml", "ini", "conf", "cfg", "env", "xml", "mod", "sum", "lock", "jsonl" -> Icons.Outlined.DataObject
        else -> if (f.name.equals("Makefile", true) || f.name.equals("Dockerfile", true)) Icons.Outlined.Build
        else Icons.AutoMirrored.Outlined.InsertDriveFile
    }
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
            .clip(MaterialTheme.shapes.small)
            .background(Tok.hover)
            .horizontalScroll(scroll)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "/",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Readout),
            color = if (segments.isEmpty()) Tok.text else Tok.muted,
            modifier = Modifier.clickable { files.cd(if (isGw) "" else "/") }.padding(vertical = 6.dp, horizontal = 2.dp),
        )
        segments.forEachIndexed { i, seg ->
            val last = i == segments.lastIndex
            Text(
                seg,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Readout),
                color = if (last) Tok.text else Tok.muted,
                maxLines = 1,
                modifier = Modifier
                    .clickable(enabled = !last) {
                        val target = segments.take(i + 1).joinToString("/")
                        files.cd(if (isGw) target else "/$target")
                    }
                    .padding(vertical = 6.dp),
            )
            if (!last) {
                Text("/", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Readout), color = Tok.faint)
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
            color = Tok.muted,
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
            art = PixelArt.Folder,
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
                    color = Tok.muted,
                )
            }
        }
    }
}

// İskelet satırı: ikon karosu + iki çizgi, faint zemin, ListRow ölçüsünde.
@Composable
private fun SkeletonRow(widthFraction: Float) {
    val c = Tok.hover
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(c))
        Spacer(Modifier.width(Space.md))
        Column {
            Box(Modifier.fillMaxWidth(widthFraction).height(12.dp).clip(RoundedCornerShape(3.dp)).background(c))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(widthFraction * 0.5f).height(9.dp).clip(RoundedCornerShape(3.dp)).background(c))
        }
    }
}
