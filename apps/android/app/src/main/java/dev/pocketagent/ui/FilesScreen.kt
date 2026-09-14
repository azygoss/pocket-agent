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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
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
fun FilesScreen(files: FilesViewModel) {
    val context = LocalContext.current

    // Oturum açıldığında home dizinine gir
    LaunchedEffect(files.hasActiveSftp()) { files.open() }

    // İndirme tamamlanınca paylaşım sayfası
    LaunchedEffect(files.downloaded) {
        val f = files.downloaded ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, f.name))
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

        // Geri tuşu önce bir üst dizine iner; kökteyse normal davranır.
        val canGoUp = if (files.mode == FilesMode.WORKSPACE) files.gwPath.isNotEmpty()
        else files.path != null && files.path != "/"
        BackHandler(enabled = canGoUp) { files.up() }

        // Yol çubuğu: dokunulabilir breadcrumb — her segment o dizine atlar.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
        }

        files.error?.let {
            Text(
                it,
                color = TermRed,
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
                LazyColumn(Modifier.fillMaxSize()) {
                    items(files.entries, key = { it.path }) { f ->
                        RemoteFileRow(f, onClick = { files.onFile(f) })
                        SoftDivider(Modifier.padding(start = 62.dp))
                    }
                }
            }
            if (files.downloading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        Text(
            if (files.mode == FilesMode.WORKSPACE) "Gateway • SSH tüneli içinde • workspace jail"
            else "SFTP • SSH oturumu içinde • indirme ≤10MB",
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
private fun RemoteFileRow(f: RemoteFile, onClick: () -> Unit) {
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
                        if (f.isDir) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
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
        onClick = onClick,
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
