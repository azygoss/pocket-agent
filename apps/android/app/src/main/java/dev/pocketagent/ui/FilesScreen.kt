// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.pocketagent.transport.RemoteFile
import dev.pocketagent.ui.theme.Code
import dev.pocketagent.ui.theme.PillShape
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.Tone

// P15: aktif SSH oturumunun SFTP kanalı (ya da workspace gateway'i) üzerinden
// uzak dosya gezgini. Backend içerik görmez; trafik SSH içinde kalır.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilesScreen(files: FilesViewModel, onOpenTerminal: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val notify = rememberNotify()
    val t = Tok

    var actionFile by remember { mutableStateOf<RemoteFile?>(null) }
    var renaming by remember { mutableStateOf<RemoteFile?>(null) }
    var deleting by remember { mutableStateOf<RemoteFile?>(null) }
    var mkdirIn by remember { mutableStateOf<RemoteFile?>(null) }
    var mkdirOpen by remember { mutableStateOf(false) }
    var gotoOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<RemoteFile>() }
    var deletingMany by remember { mutableStateOf<List<RemoteFile>?>(null) }
    var filterOpen by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var grepOpen by remember { mutableStateOf(false) }
    var devServerOpen by remember { mutableStateOf(false) }

    // Oturum açıldığında home dizinine gir
    LaunchedEffect(files.hasActiveSftp()) { files.open() }

    // İndirme tamamlanınca paylaşım sayfası (tek dosya → SEND, çoklu → SEND_MULTIPLE)
    LaunchedEffect(files.downloaded) {
        val list = files.downloaded ?: return@LaunchedEffect
        if (list.isEmpty()) { files.clearDownloaded(); return@LaunchedEffect }
        val uris = list.map { FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it) }
        val share = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
        }
        share.type = "*/*"
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(share, list.first().name))
        files.clearDownloaded()
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val (name, bytes) = pickedFile(context, uri) ?: return@rememberLauncherForActivityResult
        if (bytes.size <= 10 * 1024 * 1024) files.upload(name, bytes) else notify("10 MB sınırı aşıldı") // P15: 10MB cap
    }

    val isGw = files.mode == FilesMode.WORKSPACE
    // Geri tuşu: önce seçim, sonra bu ekranda girilen alt dizinlerden yukarı;
    // giriş dizinine dönünce ekrandan çıkar.
    var entryPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(files.path) { if (entryPath == null && files.path != null) entryPath = files.path }
    val canGoUp = if (isGw) files.gwPath.isNotEmpty() else files.path != null && files.path != "/"
    val backGoesUp = if (isGw) files.gwPath.isNotEmpty()
    else entryPath != null && files.path != null && files.path != entryPath && files.path!!.startsWith(entryPath!!.trimEnd('/') + "/")
    BackHandler(enabled = backGoesUp && selected.isEmpty()) { files.up() }
    BackHandler(enabled = selected.isNotEmpty()) { selected.clear() }

    Column(Modifier.fillMaxSize().background(t.bg).windowInsetsPadding(WindowInsets.navigationBars)) {
        if (!files.hasActiveSftp()) {
            PocketTopBar("Dosyalar", onBack = onBack)
            NoSession()
            return@Column
        }

        if (selected.isNotEmpty()) {
            PocketTopBar(
                "${selected.size} seçildi",
                onBack = { selected.clear() },
                backIcon = Icons.Rounded.Close,
                backDesc = "Seçimi kapat",
            ) {
                IconAction(Icons.Rounded.SelectAll, "Tümünü seç", {
                    files.entries.filter { it !in selected }.forEach { selected.add(it) }
                })
                IconAction(Icons.Rounded.Download, "İndir", {
                    files.downloadAll(selected.toList()); selected.clear()
                }, enabled = !isGw && selected.any { !it.isDir })
                IconAction(Icons.Rounded.ContentCopy, "Yolları kopyala", {
                    clipboard.setText(AnnotatedString(selected.joinToString("\n") { it.path }))
                    notify("${selected.size} yol kopyalandı")
                    selected.clear()
                })
                if (!isGw) IconAction(Icons.Rounded.Delete, "Sil", { deletingMany = selected.toList() }, tint = t.danger.fg)
            }
        } else {
            PocketTopBar(
                "Dosyalar",
                onBack = onBack,
                subtitle = {
                    Text(
                        listOfNotNull(files.hostLabel(), if (isGw) "workspace" else "SFTP").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code),
                        maxLines = 1,
                    )
                },
            ) {
                IconAction(Icons.Rounded.Search, "Bu dizinde ara", { filterOpen = !filterOpen; if (!filterOpen) filter = "" }, selected = filterOpen)
                if (!isGw) IconAction(Icons.Rounded.Upload, "Dosya yükle", { uploadLauncher.launch("*/*") }, enabled = !files.loading)
                Box {
                    IconAction(Icons.Rounded.MoreVert, "Diğer", { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = t.raised) {
                        if (!isGw) FileMenuItem(Icons.Rounded.TravelExplore, "İçerikte ara") { menuOpen = false; grepOpen = true }
                        FileMenuItem(Icons.Rounded.FolderOpen, "Yola git") { menuOpen = false; gotoOpen = true }
                        if (!isGw) FileMenuItem(Icons.Rounded.CreateNewFolder, "Yeni klasör") { menuOpen = false; mkdirOpen = true }
                        FileMenuItem(Icons.Rounded.Refresh, "Yenile") { menuOpen = false; files.refresh() }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = Space.lg)) {
            SegmentedTabs(
                options = listOf(Segment(FilesMode.SFTP, "SFTP"), Segment(FilesMode.WORKSPACE, "Workspace")),
                selected = files.mode,
                onSelect = { files.selectMode(it); selected.clear() },
                modifier = Modifier.fillMaxWidth(),
            )
            if (filterOpen) {
                Spacer(Modifier.height(Space.md))
                PocketTextField(
                    filter, { filter = it },
                    placeholder = "Bu dizinde ada göre süz",
                    leading = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailing = { IconAction(Icons.Rounded.Close, "Aramayı kapat", { filter = ""; filterOpen = false }) },
                )
            }
        }

        if (isGw && files.gatewayAvailable == false) {
            WorkspaceMissing()
            return@Column
        }

        if (isGw) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                listOf("staged" to "Staged", "unstaged" to "Unstaged", "working" to "Working", "last" to "Son commit").forEach { (kind, label) ->
                    ToolChip(Icons.Rounded.Difference, label, t.live) { files.gwDiff(kind, "git diff ($label)") }
                }
                ToolChip(Icons.Rounded.Language, "Dev server", t.busy) { devServerOpen = true }
                ToolChip(Icons.Rounded.Forum, "Agent sohbetleri", t.accent) { files.loadTranscripts() }
            }
            files.transcripts?.let { list ->
                Column(Modifier.padding(horizontal = Space.lg)) {
                    if (list.isEmpty()) {
                        Text("Son agent sohbeti bulunamadı.", style = MaterialTheme.typography.bodyMedium, color = t.text2, modifier = Modifier.padding(bottom = Space.md))
                    } else {
                        list.take(5).forEachIndexed { i, tr ->
                            GroupRow(i, list.take(5).size) {
                                ListRow(
                                    title = tr.rel.substringAfterLast('/').removeSuffix(".jsonl"),
                                    titleMono = true,
                                    subtitle = "${tr.src} · ${relativeTime(tr.mtime * 1000)}",
                                    leading = { IconTile(Icons.Rounded.Forum, tone = t.accent, size = 36.dp) },
                                    onClick = { files.openTranscript(tr) },
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.md))
                    }
                }
            }
        }

        // Yol çubuğu: dokunulabilir kırıntılar — her parça o dizine atlar.
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = Space.lg, top = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.Rounded.ArrowUpward, "Üst dizin", { files.up() }, enabled = canGoUp)
            PathBar(files, Modifier.weight(1f))
        }

        files.error?.let {
            Banner(Status.Error, it, modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val shown = if (filter.isBlank()) files.entries.toList()
            else files.entries.filter { it.name.contains(filter, ignoreCase = true) }
            when {
                // İskelet: gerçek satır geometrisinde loş bloklar — liste
                // geldiğinde düzen kaymaz. Tek okuma "yükleniyor".
                files.loading && files.entries.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(horizontal = Space.lg).semantics { contentDescription = "Yükleniyor" },
                ) {
                    repeat(7) { i -> SkeletonRow(listOf(0.42f, 0.6f, 0.35f, 0.5f, 0.7f, 0.3f, 0.55f)[i]) }
                }
                shown.isEmpty() && !files.loading -> Text(
                    if (filter.isNotBlank()) "\"$filter\" ile eşleşen öğe yok." else "Bu dizin boş.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.text2,
                    modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.lg),
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.sm, bottom = Space.xxl),
                ) {
                    itemsIndexed(shown, key = { _, f -> f.path }, contentType = { _, f -> if (f.isDir) 0 else 1 }) { i, f ->
                        GroupRow(i, shown.size) {
                            RemoteFileRow(
                                f,
                                selecting = selected.isNotEmpty(),
                                selected = f in selected,
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        if (f in selected) selected.remove(f) else selected.add(f)
                                    } else {
                                        files.onFile(f)
                                    }
                                },
                                onLongClick = { if (selected.isEmpty()) actionFile = f else if (f !in selected) selected.add(f) },
                            )
                        }
                    }
                    item(key = "footer") {
                        Text(
                            (if (isGw) "Gateway · SSH tüneli · workspace sınırında" else "SFTP · SSH oturumu içinde · indirme ≤10 MB") +
                                " · ${files.entries.size} öğe",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.text2,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = Space.md),
                        )
                    }
                }
            }
            if (files.downloading || (files.loading && files.entries.isNotEmpty())) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Space.lg)
                        .clip(PillShape)
                        .background(t.raised)
                        .padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = t.primary)
                    Spacer(Modifier.width(Space.md))
                    Text(if (files.downloading) "İndiriliyor…" else "Yükleniyor…", style = MaterialTheme.typography.labelLarge, color = t.text)
                }
            }
        }
    }

    // P14: agent transcript sohbet görünümü
    files.chatBlocks?.let { (name, blocks) ->
        ChatDialog(title = name, blocks = blocks, onClose = { files.dismissChat() })
    }

    // Önizleme: tam ekran okuyucu (diff içeriği renklendirilir).
    files.preview?.let { (name, content) ->
        TextViewer(
            title = name,
            content = content,
            diff = name.startsWith("git diff"),
            onCopy = { clipboard.setText(AnnotatedString(content)); notify("Kopyalandı") },
            onClose = { files.dismissPreview() },
        )
    }

    // ── Uzun basma eylemleri ────────────────────────────────────────────────
    actionFile?.let { f ->
        PocketSheet(onDismiss = { actionFile = null }, title = f.name, subtitle = f.path, subtitleMono = true) { dismiss ->
            SheetAction(Icons.Rounded.ContentCopy, "Yolu kopyala", onClick = {
                clipboard.setText(AnnotatedString(f.path)); notify("Yol kopyalandı"); dismiss()
            })
            SheetAction(Icons.Rounded.Terminal, "Terminale yaz", sublabel = "Yol imlece yazılır; Enter sende", onClick = {
                if (files.pastePathToTerminal(f)) { actionFile = null; onOpenTerminal() } else { notify("Aktif oturum yok"); dismiss() }
            })
            SheetAction(Icons.Rounded.CheckCircle, "Seç", sublabel = "Çoklu seçim başlar", onClick = {
                if (f !in selected) selected.add(f); dismiss()
            })
            if (!isGw) {
                if (!f.isDir) {
                    SheetAction(Icons.Rounded.Download, "İndir ve paylaş", onClick = { files.download(f); dismiss() })
                } else {
                    SheetAction(Icons.Rounded.CreateNewFolder, "İçinde yeni klasör", onClick = { mkdirIn = f; dismiss() })
                }
                SheetAction(Icons.Rounded.DriveFileRenameOutline, "Yeniden adlandır", onClick = { renaming = f; dismiss() })
                InsetDivider(Space.xxl)
                SheetAction(Icons.Rounded.Delete, "Sil", danger = true, onClick = { deleting = f; dismiss() })
            }
        }
    }

    renaming?.let { f ->
        var name by remember { mutableStateOf(f.name) }
        PocketDialog(
            onDismiss = { renaming = null },
            title = "Yeniden adlandır",
            content = { PocketTextField(name, { name = it }, label = "Yeni ad", mono = true) },
            confirm = { GhostButton("Kaydet", enabled = name.isNotBlank() && !name.contains('/'), onClick = { files.rename(f, name); renaming = null }) },
            dismiss = { GhostButton("Vazgeç", onClick = { renaming = null }, color = t.text2) },
        )
    }

    if (mkdirOpen || mkdirIn != null) {
        var name by remember { mutableStateOf("") }
        PocketDialog(
            onDismiss = { mkdirOpen = false; mkdirIn = null },
            title = "Yeni klasör",
            content = {
                PocketTextField(
                    name, { name = it },
                    label = "Klasör adı", mono = true,
                    helper = "Konum: " + (mkdirIn?.path ?: files.path ?: "/"),
                )
            },
            confirm = {
                GhostButton("Oluştur", enabled = name.isNotBlank() && !name.contains('/'), onClick = {
                    files.mkdir(name, mkdirIn); mkdirOpen = false; mkdirIn = null
                })
            },
            dismiss = { GhostButton("Vazgeç", onClick = { mkdirOpen = false; mkdirIn = null }, color = t.text2) },
        )
    }

    if (gotoOpen) {
        var target by remember { mutableStateOf(if (isGw) files.gwPath else files.path ?: "/") }
        PocketDialog(
            onDismiss = { gotoOpen = false },
            title = "Yola git",
            content = {
                PocketTextField(
                    target, { target = it },
                    label = "Dizin", mono = true,
                    helper = if (isGw) "Workspace köküne göreli." else "Göreli yol bu dizinden başlar.",
                )
            },
            confirm = {
                GhostButton("Git", onClick = {
                    val tr = target.trim()
                    if (tr.isNotEmpty() || isGw) {
                        // SFTP'de göreli yol cwd'ye bağlanır; workspace zaten jail-göreli.
                        val abs = if (!isGw && !tr.startsWith("/")) (files.path ?: "/").trimEnd('/') + "/" + tr else tr
                        files.cd(abs)
                    }
                    gotoOpen = false
                })
            },
            dismiss = { GhostButton("Vazgeç", onClick = { gotoOpen = false }, color = t.text2) },
        )
    }

    deleting?.let { f ->
        ConfirmDialog(
            title = "${f.name} silinsin mi?",
            message = if (f.isDir) "Klasör ve içindeki her şey host'tan kalıcı olarak silinir." else "Dosya host'tan kalıcı olarak silinir.",
            confirmLabel = "Sil",
            onConfirm = { files.delete(f); deleting = null },
            onDismiss = { deleting = null },
        )
    }
    deletingMany?.let { fs ->
        ConfirmDialog(
            title = "${fs.size} öğe silinsin mi?",
            message = "Seçili dosyalar ve klasörler (içerikleriyle birlikte) host'tan kalıcı olarak silinir.",
            confirmLabel = "Sil",
            onConfirm = { files.deleteAll(fs); deletingMany = null; selected.clear() },
            onDismiss = { deletingMany = null },
        )
    }

    if (grepOpen) {
        var pattern by remember { mutableStateOf("") }
        PocketDialog(
            onDismiss = { grepOpen = false },
            title = "İçerikte ara",
            content = {
                PocketTextField(
                    pattern, { pattern = it },
                    label = "Aranacak metin", mono = true,
                    helper = "Bu dizinde alt klasörlerle birlikte; .git ve ikili dosyalar atlanır.",
                )
            },
            confirm = { GhostButton("Ara", enabled = pattern.isNotBlank(), onClick = { files.grep(pattern); grepOpen = false }) },
            dismiss = { GhostButton("Vazgeç", onClick = { grepOpen = false }, color = t.text2) },
        )
    }

    // Grep sonuçları: yol:satır → dokununca üst dizine iner + önizleme açar.
    if (files.grepRunning || files.grepResults != null) {
        val hits = files.grepResults.orEmpty()
        PocketSheet(
            onDismiss = { files.dismissGrep() },
            title = if (files.grepRunning) "Aranıyor…" else "${hits.size} eşleşme",
        ) { dismiss ->
            if (files.grepRunning) {
                Box(Modifier.fillMaxWidth().padding(Space.xxl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = t.primary)
                }
            } else if (hits.isEmpty()) {
                Text("Eşleşme yok.", style = MaterialTheme.typography.bodyMedium, color = t.text2, modifier = Modifier.padding(horizontal = Space.xxl))
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    itemsIndexed(hits, key = { _, h -> "${h.path}:${h.line}:${h.text}" }) { _, h ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .tap({ files.revealPath(h.path); dismiss() })
                                .padding(horizontal = Space.xxl, vertical = Space.sm + 2.dp),
                        ) {
                            Text(
                                "${h.path}:${h.line}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code),
                                color = t.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                h.text.trim(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code),
                                color = t.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    // Dev-server önizleme (yalnız host loopback'i — SSRF korumalı).
    if (devServerOpen) {
        var port by remember { mutableStateOf("") }
        var path by remember { mutableStateOf("/") }
        PocketDialog(
            onDismiss = { devServerOpen = false },
            title = "Dev server önizleme",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                    Text("Yalnız host üzerindeki 127.0.0.1 adresleri; içerik metin olarak gösterilir.", style = MaterialTheme.typography.bodyMedium)
                    PocketTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = "Port", placeholder = "3000", mono = true)
                    PocketTextField(path, { path = it }, label = "Yol", mono = true)
                }
            },
            confirm = {
                GhostButton("Getir", enabled = port.toIntOrNull() in 1..65535, onClick = {
                    port.toIntOrNull()?.let { files.gwPreview(it, path) }
                    devServerOpen = false
                })
            },
            dismiss = { GhostButton("Vazgeç", onClick = { devServerOpen = false }, color = t.text2) },
        )
    }
}

@Composable
private fun FileMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = Tok.text) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Tok.text2) },
        onClick = onClick,
    )
}

@Composable
private fun ToolChip(icon: ImageVector, label: String, tone: Tone, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(PillShape)
            .background(tone.soft)
            .tap(onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tone.onSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tone.onSoft, maxLines = 1)
    }
}

// Tam ekran metin okuyucu: mono, seçilebilir; diff satırları renklenir.
@Composable
private fun TextViewer(title: String, content: String, diff: Boolean, onCopy: () -> Unit, onClose: () -> Unit) {
    val t = Tok
    FullScreenDialog(onDismiss = onClose) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))) {
            PocketTopBar(title, onBack = onClose, backIcon = Icons.Rounded.Close, backDesc = "Kapat", insetTop = false) {
                IconAction(Icons.Rounded.ContentCopy, "İçeriği kopyala", onCopy)
            }
            SelectionContainer(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.lg, vertical = Space.sm),
                ) {
                    Text(
                        if (diff) diffAnnotated(content, t) else AnnotatedString(content),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code, fontSize = 12.5.sp, lineHeight = 18.sp),
                        color = t.text,
                    )
                    Spacer(Modifier.height(Space.x3))
                }
            }
        }
    }
}

// Diff renklendirme: + yeşil, - mercan, @@ mavi, başlıklar kalın, +++/--- soluk.
private fun diffAnnotated(content: String, t: dev.pocketagent.ui.theme.PocketTokens): AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        content.lines().forEach { line ->
            val style = when {
                line.startsWith("+++") || line.startsWith("---") -> androidx.compose.ui.text.SpanStyle(color = t.text2)
                line.startsWith("+") -> androidx.compose.ui.text.SpanStyle(color = t.live.text, background = t.live.soft)
                line.startsWith("-") -> androidx.compose.ui.text.SpanStyle(color = t.danger.text, background = t.danger.soft)
                line.startsWith("@@") -> androidx.compose.ui.text.SpanStyle(color = t.busy.text)
                line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("commit ") ->
                    androidx.compose.ui.text.SpanStyle(color = t.text, fontWeight = FontWeight.Bold)
                else -> null
            }
            if (style != null) withStyle(style) { append(line); append('\n') } else { append(line); append('\n') }
        }
    }

// Dosya satırı: türünü söyleyen renkli ikon karosu, mono ad, boyut · zaman.
@Composable
private fun RemoteFileRow(
    f: RemoteFile,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = Tok
    val (icon, tone) = fileKind(f)
    ListRow(
        title = f.name,
        titleMono = true,
        subtitle = listOfNotNull(
            if (!f.isDir) humanSize(f.size) else null,
            if (f.mtime > 0) relativeTime(f.mtime * 1000) else null,
        ).joinToString(" · ").ifBlank { null },
        modifier = Modifier.background(if (selected) t.accent.soft else androidx.compose.ui.graphics.Color.Transparent),
        leading = { IconTile(icon, tone = tone, size = 38.dp) },
        trailing = {
            when {
                selecting -> Icon(
                    if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (selected) "Seçili" else "Seçili değil",
                    tint = if (selected) t.primary else t.text3,
                    modifier = Modifier.size(22.dp),
                )
                f.isDir -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = t.text3, modifier = Modifier.size(20.dp))
                else -> {}
            }
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
private fun fileKind(f: RemoteFile): Pair<ImageVector, Tone?> {
    val t = Tok
    if (f.isDir) return Icons.Outlined.Folder to t.accent
    val ext = f.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts", "java", "go", "rs", "py", "js", "ts", "tsx", "jsx", "c", "h", "cpp", "rb", "swift", "sh", "zsh", "bash", "lua", "php" -> Icons.Outlined.Code to t.busy
        "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "heic" -> Icons.Outlined.Image to t.violet
        "zip", "gz", "tgz", "tar", "xz", "bz2", "7z", "rar", "zst", "apk", "jar" -> Icons.Outlined.Inventory2 to t.warn
        "md", "txt", "rst", "log", "pdf", "doc", "docx" -> Icons.Outlined.Description to null
        "json", "yaml", "yml", "toml", "ini", "conf", "cfg", "env", "xml", "mod", "sum", "lock", "jsonl" -> Icons.Outlined.DataObject to null
        else -> if (f.name.equals("Makefile", true) || f.name.equals("Dockerfile", true)) Icons.Outlined.Build to null
        else Icons.AutoMirrored.Outlined.InsertDriveFile to null
    }
}

// Kırıntı çubuğu: "/a/b/c" → ⌂ / a / b / c; her parça o derinliğe gider.
// Yol uzayınca en derin parça görünür kalır (sona otomatik kayar).
@Composable
private fun PathBar(files: FilesViewModel, modifier: Modifier = Modifier) {
    val isGw = files.mode == FilesMode.WORKSPACE
    val rel = if (isGw) files.gwPath else (files.path ?: "").trim('/')
    val segments = rel.split('/').filter { it.isNotEmpty() }
    val scroll = rememberScrollState()
    LaunchedEffect(rel) { scroll.scrollTo(scroll.maxValue) }
    val t = Tok
    Row(
        modifier
            .clip(PillShape)
            .background(t.card)
            .horizontalScroll(scroll)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(PillShape)
                .tap({ files.cd(if (isGw) "" else "/") })
                .heightIn(min = 40.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isGw) Icons.Rounded.Folder else Icons.Rounded.Home,
                contentDescription = if (isGw) "Workspace kökü" else "Kök dizin",
                tint = if (segments.isEmpty()) t.text else t.text2,
                modifier = Modifier.size(18.dp),
            )
        }
        segments.forEachIndexed { i, seg ->
            val last = i == segments.lastIndex
            Text("/", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Code), color = t.text3)
            Text(
                seg,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Code, fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal),
                color = if (last) t.text else t.text2,
                maxLines = 1,
                modifier = Modifier
                    .clip(PillShape)
                    .tap(if (last) null else {
                        {
                            val target = segments.take(i + 1).joinToString("/")
                            files.cd(if (isGw) target else "/$target")
                        }
                    })
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            )
        }
    }
}

// SAF seçici sonucu: görünen ad + baytlar (null = okunamadı).
// Dosya yükleme ve terminal agent-ek akışı paylaşır.
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
private fun WorkspaceMissing() {
    Column(Modifier.padding(Space.lg)) {
        PocketCard {
            Text("Workspace gateway kurulu değil", style = MaterialTheme.typography.titleMedium, color = Tok.text)
            Spacer(Modifier.height(Space.sm))
            Text(
                "Gateway; git diff, dev server önizlemesi ve agent sohbetlerini SSH tüneli üzerinden sunar. Host'ta çalıştır:",
                style = MaterialTheme.typography.bodyMedium,
                color = Tok.text2,
            )
            Spacer(Modifier.height(Space.md))
            ValueBox("pocket-agent service install-gateway")
            Spacer(Modifier.height(Space.md))
            Text(
                "Yalnız 127.0.0.1:24543 dinler. Token ~/.config/pocket-agent/gateway.token dosyasındadır; uygulama onu oturum içinden okur.",
                style = MaterialTheme.typography.bodySmall,
                color = Tok.text2,
            )
        }
    }
}

@Composable
private fun NoSession() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        EmptyState(
            icon = Icons.Rounded.Folder,
            title = "Uzak dosyalar",
            body = "Aktif bir SSH oturumu yok. Bir host'a bağlandığında bu ekran aynı oturumun SFTP kanalıyla uzak dosya sistemini gösterir.",
        )
        Text(
            "Dosyalar yalnız SSH tünelinde akar, backend içerik görmez. İndirmeler paylaşım önbelleğine düşer (10 MB üst sınır).",
            style = MaterialTheme.typography.bodySmall,
            color = Tok.text2,
            modifier = Modifier.padding(horizontal = Space.x3).align(Alignment.CenterHorizontally),
        )
    }
}

// İskelet satırı: ikon karosu + iki çizgi, satır ölçüsünde, shimmer yok.
@Composable
private fun SkeletonRow(widthFraction: Float) {
    val c = Tok.card
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c))
        Spacer(Modifier.width(Space.md + 2.dp))
        Column {
            Box(Modifier.fillMaxWidth(widthFraction).height(12.dp).clip(RoundedCornerShape(4.dp)).background(c))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(widthFraction * 0.5f).height(9.dp).clip(RoundedCornerShape(4.dp)).background(c))
        }
    }
}
