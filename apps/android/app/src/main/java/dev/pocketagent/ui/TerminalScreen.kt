// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.pocketagent.ui.theme.TerminalFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.TerminalController
import dev.pocketagent.transport.TermLine
import dev.pocketagent.transport.TerminalInput
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TransportFailure
import dev.pocketagent.ui.theme.TermAmber
import dev.pocketagent.ui.theme.TermBg
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed
import dev.pocketagent.ui.theme.TermText
import kotlinx.coroutines.launch

fun TermLine.toAnnotatedString(cursorCol: Int = -1, cursorBg: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    var pos = 0
    spans.forEach { s ->
        val spanStyle = SpanStyle(
            color = s.style.fg?.let { Color(it) } ?: if (s.style.link != null) Color(0xFF58A6FF) else Color.Unspecified,
            background = s.style.bg?.let { Color(it) } ?: Color.Unspecified,
            fontWeight = if (s.style.bold) FontWeight.Bold else null,
            textDecoration = if (s.style.underline || s.style.link != null) TextDecoration.Underline else null,
        )
        val block: AnnotatedString.Builder.() -> Unit = {
            val start = pos
            pos += s.text.length
            if (cursorCol in start until pos && cursorBg != Color.Unspecified) {
                val off = cursorCol - start
                append(s.text.substring(0, off))
                withStyle(SpanStyle(background = cursorBg, color = Color.Black)) {
                    append(s.text.substring(off, off + 1))
                }
                append(s.text.substring(off + 1))
            } else {
                append(s.text)
            }
        }
        val link = s.style.link
        if (link != null) {
            // Tıklanabilir link (compose LinkAnnotation → UriHandler)
            withLink(LinkAnnotation.Url(link)) {
                withStyle(spanStyle) { block() }
            }
        } else {
            withStyle(spanStyle) { block() }
        }
    }
    // İmleç satır sonundaysa boş blok çiz
    if (cursorCol >= pos && cursorCol >= 0 && cursorBg != Color.Unspecified) {
        withStyle(SpanStyle(background = cursorBg)) { append(' ') }
    }
}

@Composable
fun TerminalScreen(
    manager: SessionManager,
    settings: SettingsViewModel,
    onNewConnection: () -> Unit,
) {
    val sessionList by manager.sessions.collectAsState()
    val activeId by manager.activeId.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Oturum çipleri
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sessionList.forEach { h ->
                val st by h.controller.state.collectAsState()
                FilterChip(
                    selected = h.id == activeId,
                    onClick = { manager.setActive(h.id) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StateDot(st)
                            Spacer(Modifier.width(6.dp))
                            Text(h.conn.name)
                        }
                    },
                )
            }
            IconButton(onClick = onNewConnection) {
                Icon(Icons.Filled.Add, contentDescription = "Yeni bağlantı")
            }
        }

        val active = sessionList.firstOrNull { it.id == activeId }
        if (active == null) {
            EmptyTerminal()
        } else {
            ActiveTerminal(
                controller = active.controller,
                settings = settings,
                onClose = { manager.close(active.id) },
            )
        }
    }
}

@Composable
private fun EmptyTerminal() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$ açık oturum yok",
            color = TermGreen,
            fontFamily = TerminalFont,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Bağlantılar sekmesinden bir host seç. Her host kendi oturumuyla açılır; çiplerle aralarında gezinebilirsin.",
            color = TermText.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ActiveTerminal(
    controller: TerminalController,
    settings: SettingsViewModel,
    onClose: () -> Unit,
) {
    val vm = controller.vm
    val lines by vm.lines.collectAsState()
    val cursor by vm.cursor.collectAsState()
    val windowTitle by vm.windowTitle.collectAsState()
    val pendingClip by vm.pendingClipboard.collectAsState()
    val state by controller.state.collectAsState()
    val failure by controller.failure.collectAsState()
    val active by controller.connectedTo.collectAsState()
    var text by remember { mutableStateOf("") }
    var ctrl by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // OSC 52: uzak taraf (tmux/vim) panoya yazdı → cihaz panosuna aktar.
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(pendingClip) {
        pendingClip?.let {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(it))
            vm.consumeClipboard()
        }
    }

    // Tam ekran: sistem çubuklarını gizle (vim/htop için maksimum alan).
    val view = LocalView.current
    DisposableEffect(fullscreen) {
        val window = (view.context as? android.app.Activity)?.window
        val ic = window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen && ic != null) {
            ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ic.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ic?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ic?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Kullanıcı sondaysa otomatik kaydır; yukarıdaysa rahatsız etme.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 1
        }
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && atBottom) listState.scrollToItem(lines.size - 1)
    }

    val matches = remember(lines, query) {
        if (query.length < 2) emptyList()
        else lines.mapIndexedNotNull { i, l -> if (l.text.contains(query, ignoreCase = true)) i else null }
    }
    // itemsIndexed içinde O(n·m) contains yerine set sorgusu
    val matchSet = remember(matches) { matches.toSet() }
    var matchCursor by remember { mutableStateOf(0) }
    val currentMatch = matches.getOrNull(matchCursor)

    fun sendText(s: String) {
        controller.send(TerminalInput.Text(s))
    }

    fun sendInput() {
        if (text.isEmpty()) return
        vm.pushHistory(text)
        if (ctrl) {
            // Ctrl aktif: ilk karakter kontrol koduna çevrilir, newline eklenmez.
            val c = text.first()
            sendText(((c.code and 0x1F).toChar()).toString() + text.drop(1))
            ctrl = false
        } else {
            sendText(text + "\n")
        }
        text = ""
    }

    Column {
        // Durum çubuğu (tam ekranda gizli)
        if (!fullscreen) {
            val retryAttempt by controller.retryAttempt.collectAsState()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StateDot(state)
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = {},
                shape = MaterialTheme.shapes.extraSmall,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                label = {
                    Text(
                        when {
                            retryAttempt > 0 -> "Yeniden bağlanıyor ($retryAttempt/${dev.pocketagent.transport.TerminalController.MAX_RETRY})…"
                            state == ConnectionState.ACTIVE -> "${vm.badge} • ${active?.host}" +
                                (if (windowTitle.isNotBlank()) " • $windowTitle" else "")
                            state == ConnectionState.CONNECTING -> "Bağlanıyor…"
                            state == ConnectionState.FAILED -> "Hata"
                            else -> "Bağlı değil"
                        },
                        maxLines = 1,
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                Icon(Icons.Filled.Search, contentDescription = "Scrollback'te ara")
            }
            IconButton(onClick = {
                // Scrollback'i dosyaya döküp paylaş (P15 paylaşım yüzeyi)
                val dump = lines.joinToString("\n") { it.text }
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
                    val f = java.io.File(dir, "scrollback.txt")
                    f.writeText(dump)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider", f,
                        )
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(share, "Scrollback"))
                    }
                }
            }) {
                Icon(Icons.Filled.Share, contentDescription = "Scrollback'i paylaş")
            }
            IconButton(onClick = { fullscreen = true }) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Tam ekran")
            }
            when (state) {
                ConnectionState.CLOSED, ConnectionState.FAILED -> {
                    if (controller.canReconnect()) {
                        TextButton(onClick = { controller.reconnect() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Yeniden bağlan")
                        }
                    }
                }
                else -> {}
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Oturumu kapat", tint = MaterialTheme.colorScheme.error)
            }
        }
        }

        // Arama çubuğu
        if (searchOpen) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; matchCursor = 0 },
                    placeholder = { Text("Scrollback'te ara…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (matches.isEmpty()) "0" else "${matchCursor + 1}/${matches.size}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(
                    enabled = matches.isNotEmpty(),
                    onClick = {
                        matchCursor = (matchCursor + 1) % matches.size
                        scope.launch { listState.scrollToItem(matches[matchCursor]) }
                    },
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sonraki eşleşme") }
            }
        }

        // Hata kartı
        if (state == ConnectionState.FAILED && failure != null) {
            Surface(
                color = TermRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Text(
                    failureText(failure!!),
                    color = TermRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        // Terminal yüzeyi — ölçülen boyut PTY'ye resize olarak gider.
        val termBg = Color(settings.theme.palette.background)
        val density = LocalDensity.current
        val charW = with(density) { (13 * settings.theme.fontScale).sp.toPx() } * 0.6f
        val lineH = with(density) { (16 * settings.theme.fontScale).sp.toPx() }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp)) {
            val surfaceFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            Surface(
                color = termBg,
                shape = if (fullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxSize()
                    .semantics { contentDescription = "Terminal çıktısı" }
                    .focusRequester(surfaceFocus)
                    .focusable()
                    // Donanım klavyesi: Ctrl+harf → kontrol kodu, oklar/Home/End/PgUp/PgDn/Esc
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (state != ConnectionState.ACTIVE) return@onPreviewKeyEvent false
                        val k = ev.key
                        when {
                            ev.isCtrlPressed && k.keyCode in androidx.compose.ui.input.key.Key.A.keyCode..androidx.compose.ui.input.key.Key.Z.keyCode -> {
                                val letter = 'a'.code + (k.keyCode - androidx.compose.ui.input.key.Key.A.keyCode).toInt()
                                sendText(((letter and 0x1F).toChar()).toString()); true
                            }
                            k == androidx.compose.ui.input.key.Key.DirectionUp -> { sendText("\u001B[A"); true }
                            k == androidx.compose.ui.input.key.Key.DirectionDown -> { sendText("\u001B[B"); true }
                            k == androidx.compose.ui.input.key.Key.DirectionRight -> { sendText("\u001B[C"); true }
                            k == androidx.compose.ui.input.key.Key.DirectionLeft -> { sendText("\u001B[D"); true }
                            k == androidx.compose.ui.input.key.Key.MoveHome -> { sendText("\u001B[H"); true }
                            k == androidx.compose.ui.input.key.Key.MoveEnd -> { sendText("\u001B[F"); true }
                            k == androidx.compose.ui.input.key.Key.PageUp -> { sendText("\u001B[5~"); true }
                            k == androidx.compose.ui.input.key.Key.PageDown -> { sendText("\u001B[6~"); true }
                            k == androidx.compose.ui.input.key.Key.Escape -> { sendText("\u001B"); true }
                            else -> false
                        }
                    }
                    .onSizeChanged { sz ->
                        val pad = with(density) { 16.dp.toPx() }
                        val cols = ((sz.width - pad) / charW).toInt().coerceIn(20, 500)
                        val rows = (sz.height / lineH).toInt().coerceIn(4, 200)
                        val newSize = TerminalSize(cols, rows)
                        if (newSize != vm.size) {
                            vm.setSize(newSize)
                            if (state == ConnectionState.ACTIVE) controller.send(TerminalInput.Resize(newSize))
                        }
                    },
            ) {
                if (lines.size <= 1 && lines.firstOrNull()?.text?.isBlank() != false) {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            if (state == ConnectionState.CONNECTING) "$ bağlanıyor…" else "$ bekleniyor",
                            color = if (state == ConnectionState.CONNECTING) TermAmber else TermGreen,
                            fontFamily = TerminalFont,
                            fontSize = (14 * settings.theme.fontScale).sp,
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            itemsIndexed(lines) { idx, line ->
                                val isMatch = currentMatch == idx
                                val hasMatch = matchSet.contains(idx)
                                val cur = cursor
                                val cursorCol = if (cur != null && cur.first == idx) cur.second else -1
                                Text(
                                    line.toAnnotatedString(cursorCol, Color(settings.theme.palette.cursor)),
                                    color = TermText,
                                    fontFamily = TerminalFont,
                                    fontSize = (13 * settings.theme.fontScale).sp,
                                    lineHeight = (16 * settings.theme.fontScale).sp,
                                    modifier = Modifier.fillMaxWidth().background(
                                        when {
                                            isMatch -> TermAmber.copy(alpha = 0.35f)
                                            hasMatch -> TermAmber.copy(alpha = 0.12f)
                                            else -> Color.Transparent
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            if (!atBottom && lines.size > 1) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.scrollToItem(lines.size - 1) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "En alta in")
                }
            }
            if (fullscreen) {
                SmallFloatingActionButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Filled.FullscreenExit, contentDescription = "Tam ekrandan çık")
                }
            }
        }

        // Ekstra tuşlar (tam ekranda gizli)
        if (!fullscreen) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = ctrl,
                onClick = { ctrl = !ctrl },
                label = { Text("Ctrl", fontFamily = TerminalFont, fontSize = 13.sp) },
            )
            ExtraKey("Esc") { sendText("\u001B") }
            ExtraKey("Tab") { sendText("\t") }
            ExtraKey("^C") { sendText("\u0003") }
            ExtraKey("^D") { sendText("\u0004") }
            ExtraKey("^Z") { sendText("\u001A") }
            ExtraKey("^L") { sendText("\u000C") }
            ExtraKey("↑") { sendText("\u001B[A") }
            ExtraKey("↓") { sendText("\u001B[B") }
            ExtraKey("→") { sendText("\u001B[C") }
            ExtraKey("←") { sendText("\u001B[D") }
            ExtraKey("Home") { sendText("\u001B[H") }
            ExtraKey("End") { sendText("\u001B[F") }
            ExtraKey("PgUp") { sendText("\u001B[5~") }
            ExtraKey("PgDn") { sendText("\u001B[6~") }
            ExtraKey("|") { sendText("|") }
            ExtraKey("~") { sendText("~") }
            ExtraKey("/") { sendText("/") }
            ExtraKey("-") { sendText("-") }
            ExtraKey("_") { sendText("_") }
        }
        }

        // Giriş satırı: geçmiş ↑↓ + metin + gönder
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { vm.historyOlder()?.let { text = it } },
                enabled = state == ConnectionState.ACTIVE,
            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Önceki komut") }
            IconButton(
                onClick = { text = vm.historyNewer() },
                enabled = state == ConnectionState.ACTIVE,
            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Sonraki komut") }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        if (ctrl) "Ctrl aktif: harf yaz" else "Komut yaz…",
                        fontFamily = TerminalFont,
                    )
                },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Terminal girişi" },
                singleLine = true,
                enabled = state == ConnectionState.ACTIVE,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = TerminalFont),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendInput() }),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val clip = clipboard.getText()?.text ?: return@IconButton
                    if (clip.contains('\n')) {
                        // Çok satırlı yapıştırma: alan tek satır — doğrudan gönder.
                        // Bracketed paste açıksa kabuk/editör bunu komut gibi ÇALIŞTIRMAZ.
                        val wrapped = if (vm.bracketedPaste) "\u001B[200~$clip\u001B[201~" else clip
                        sendText(wrapped)
                    } else {
                        text += clip
                    }
                },
                enabled = state == ConnectionState.ACTIVE,
                modifier = Modifier.semantics { contentDescription = "Yapıştır" },
            ) {
                Icon(
                    Icons.Filled.ContentPaste,
                    contentDescription = null,
                    tint = if (state == ConnectionState.ACTIVE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { sendInput() },
                enabled = state == ConnectionState.ACTIVE && text.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = "Gönder" },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (state == ConnectionState.ACTIVE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExtraKey(label: String, onTap: () -> Unit) {
    OutlinedButton(
        onClick = onTap,
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, fontFamily = TerminalFont, fontSize = 12.sp)
    }
}

private fun failureText(f: TransportFailure): String = when (f) {
    is TransportFailure.AuthFailed -> "Kimlik doğrulama reddedildi. Parolayı/anahtarı kontrol et — fallback yok."
    is TransportFailure.HostKeyChanged -> "HOST ANAHTARI DEĞİŞTİ. Olası MITM — bağlantı durduruldu. Revoke + yeniden eşle."
    is TransportFailure.Network -> "Ağ hatası: ${f.reason}"
    is TransportFailure.MissingServer -> "Sunucu bileşeni eksik: ${f.what}"
}

fun defaultTerminalSize(): TerminalSize = TerminalSize(80, 24)
