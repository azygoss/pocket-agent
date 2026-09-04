// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.pocketagent.transport.RemoteFile
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
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        } ?: "upload.bin"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        if (bytes.size <= 10 * 1024 * 1024) files.upload(name, bytes) // P15: 10MB cap
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (!files.hasActiveSftp()) {
            NoSessionCard()
            return@Column
        }

        // Kaynak seçimi: SFTP (tüm FS) veya Workspace (gateway jail'i)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = files.mode == FilesMode.SFTP,
                onClick = { files.selectMode(FilesMode.SFTP) },
                label = { Text("SFTP") },
            )
            FilterChip(
                selected = files.mode == FilesMode.WORKSPACE,
                onClick = { files.selectMode(FilesMode.WORKSPACE) },
                label = {
                    Text(
                        when (files.gatewayAvailable) {
                            false -> "Workspace (kurulu değil)"
                            else -> "Workspace"
                        },
                    )
                },
            )
        }

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
                    OutlinedButton(onClick = { files.gwDiff(kind, "git diff ($label)") }) { Text(label, fontSize = 12.sp) }
                }
                OutlinedButton(onClick = { previewPort = "" }) { Text("Dev server…", fontSize = 12.sp) }
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

        // Yol çubuğu
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val curPath = if (files.mode == FilesMode.WORKSPACE) "/" + files.gwPath else files.path
            IconButton(
                onClick = { files.up() },
                enabled = if (files.mode == FilesMode.WORKSPACE) files.gwPath.isNotEmpty() else files.path != null && files.path != "/",
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Üst dizin")
            }
            Text(
                curPath ?: "…",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            )
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                    androidx.compose.ui.text.SpanStyle(color = dev.pocketagent.ui.theme.TermText.copy(alpha = 0.7f))
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
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (f.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (f.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                f.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (!f.isDir) append(humanSize(f.size))
                    if (f.mtime > 0) {
                        if (isNotEmpty()) append(" • ")
                        append(relativeTime(f.mtime * 1000))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    else -> "${"%.2f".format(bytes / 1024.0 / 1024.0 / 1024.0)} GB"
}

@Composable
private fun WorkspaceMissingCard() {
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workspace gateway kurulu değil", style = MaterialTheme.typography.titleMedium)
            Text(
                "Host'ta çalıştır:\n  pocket-agent gateway serve\n  veya kalıcı servis:\n  pocket-agent service install-gateway\n\n" +
                    "Token ~/.config/pocket-agent/gateway.token altında üretilir; " +
                    "uygulama onu SSH oturumu içinden okur. Gateway yalnız 127.0.0.1:24543 dinler.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoSessionCard() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = TermGreen)
                Text("Uzak dosyalar", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aktif bir SSH oturumu yok. Terminal sekmesinden bir host'a bağlan; " +
                        "bu sekme aynı oturumun SFTP kanalıyla uzak dosya sistemini gösterir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text("Güvenlik", style = MaterialTheme.typography.titleSmall)
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
