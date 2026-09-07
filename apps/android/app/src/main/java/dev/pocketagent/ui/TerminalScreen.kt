// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import dev.pocketagent.ui.theme.ConsoleBorder
import dev.pocketagent.ui.theme.ConsoleDim
import dev.pocketagent.ui.theme.TermAmber
import dev.pocketagent.ui.theme.TermBg
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed
import dev.pocketagent.ui.theme.TermText
import dev.pocketagent.ui.theme.TerminalFont
import kotlinx.coroutines.launch

fun TermLine.toAnnotatedString(cursorCol: Int = -1, cursorBg: Color = Color.Unspecified): AnnotatedString = buildAnnotatedString {
    var pos = 0
    spans.forEach { s ->
        val spanStyle = SpanStyle(
            color = s.style.fg?.let { Color(it) } ?: if (s.style.link != null) Color(0xFF5FA8F5) else Color.Unspecified,
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
            withLink(LinkAnnotation.Url(link)) {
                withStyle(spanStyle) { block() }
            }
        } else {
            withStyle(spanStyle) { block() }
        }
    }
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

    Column(Modifier.fillMaxSize()) {
        // Oturum şeridi: durum entegre mono pill'ler (+ her zaman görünür)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sessionList.forEach { h ->
                val st by h.controller.state.collectAsState()
                val retry by h.controller.retryAttempt.collectAsState()
                SessionPill(
                    name = h.conn.name,
                    state = st,
                    retry = retry,
                    active = h.id == activeId,
                    onClick = { manager.setActive(h.id) },
                )
            }
            IconButton(onClick = onNewConnection, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Yeni bağlantı",
                    modifier = Modifier.size(16.dp),
                    tint = ConsoleDim,
                )
            }
        }

        val active = sessionList.firstOrNull { it.id == activeId }
        if (active == null) {
            EmptyTerminal(onNewConnection)
        } else {
            ActiveTerminal(
                controller = active.controller,
                settings = settings,
                onClose = { manager.close(active.id) },
                onNewConnection = onNewConnection,
            )
        }
    }
}

// Oturum pill'i: ● ad (durum rengi), aktifte yeşil border; retry rozeti inline.
@Composable
private fun SessionPill(name: String, state: ConnectionState, retry: Int, active: Boolean, onClick: () -> Unit) {
    val borderColor = when {
        active -> TermGreen
        else -> ConsoleBorder
    }
    Surface(
        color = if (active) TermGreen.copy(alpha = 0.08f) else Color.Transparent,
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateDot(state)
            Spacer(Modifier.width(6.dp))
            Text(
                if (retry > 0) "$name ↻$retry/${TerminalController.MAX_RETRY}" else name,
                fontFamily = TerminalFont,
                fontSize = 11.sp,
                color = if (active) MaterialTheme.colorScheme.onSurface else ConsoleDim,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyTerminal(onNewConnection: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$ açık oturum yok",
            color = TermGreen,
            fontFamily = TerminalFont,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Bağlantılar sekmesinden bir host seç ya da QR ile eşle. Her host kendi oturumuyla açılır; yukarıdaki şeritle aralarında gezinebilirsin.",
            color = TermText.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.clickable(onClick = onNewConnection),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("❯ host ekle", fontFamily = TerminalFont, fontSize = 12.sp, color = TermGreen)
        }
    }
}

@Composable
private fun ActiveTerminal(
    controller: TerminalController,
    settings: SettingsViewModel,
    onClose: () -> Unit,
    onNewConnection: () -> Unit,
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

    // OSC 52: uzak taraf panoya yazdı → cihaz panosuna aktar.
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(pendingClip) {
        pendingClip?.let {
            clipboard.setText(AnnotatedString(it))
            vm.consumeClipboard()
        }
    }

    // Tam ekran: sistem çubuklarını gizle.
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
            val c = text.first()
            sendText(((c.code and 0x1F).toChar()).toString() + text.drop(1))
            ctrl = false
        } else {
            sendText(text + "\n")
        }
        text = ""
    }

    Column(Modifier.fillMaxSize()) {
        // ── Terminal yüzeyi (edge-to-edge, ince border) ────────────────────
        val termBg = Color(settings.theme.palette.background)
        val density = LocalDensity.current
        val charW = with(density) { (13 * settings.theme.fontScale).sp.toPx() } * 0.6f
        val lineH = with(density) { (16 * settings.theme.fontScale).sp.toPx() }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = if (fullscreen) 0.dp else 6.dp),
        ) {
            val surfaceFocus = remember { androidx.compose.ui.focus.FocusRequester() }
            Surface(
                color = termBg,
                shape = if (fullscreen) RoundedCornerShape(0.dp) else MaterialTheme.shapes.small,
                border = if (fullscreen) null
                else androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder),
                modifier = Modifier.fillMaxSize()
                    .semantics { contentDescription = "Terminal çıktısı" }
                    .focusRequester(surfaceFocus)
                    .focusable()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (state != ConnectionState.ACTIVE) return@onPreviewKeyEvent false
                        val k = ev.key
                        when {
                            ev.isCtrlPressed && k.keyCode in androidx.compose.ui.input.key.Key.A.keyCode..androidx.compose.ui.input.key.Key.Z.keyCode -> {
                                val letter = 'a'.code + (k.keyCode - androidx.compose.ui.input.key.Key.A.keyCode).toInt()
                                sendText(((letter and 0x1F).toChar()).toString()); true
                            }
                            k == androidx.compose.ui.input.key.Key.DirectionUp -> { sendText("\u001B"); true }
                            k == androidx.compose.ui.input.key.Key.DirectionDown -> { sendText("\u001B"); true }
                            k == androidx.compose.ui.input.key.Key.DirectionRight -> { sendText("\u001B"); true }
                            k == androidx.compose.ui.input.key.Key.DirectionLeft -> { sendText("\u001B"); true }
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
                            when (state) {
                                ConnectionState.CONNECTING -> "$ bağlanıyor…"
                                ConnectionState.ACTIVE -> "$ bekleniyor"
                                ConnectionState.FAILED -> "$ bağlantı hatası"
                                else -> "$ bekleniyor"
                            },
                            color = when (state) {
                                ConnectionState.CONNECTING -> TermAmber
                                ConnectionState.FAILED -> TermRed
                                else -> TermGreen
                            },
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

            // ── Overlay: üst-sağ aksiyon çubuğu (yarı saydam, içeriğin üstünde) ──
            if (!fullscreen) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color(0xCC0A0D13), MaterialTheme.shapes.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Durum metni (mono, dim): transport • host • pencere başlığı
                    Text(
                        buildString {
                            append(vm.badge)
                            active?.let { append(" · ${it.host}") }
                            if (windowTitle.isNotBlank()) append(" · $windowTitle")
                            if (state == ConnectionState.CONNECTING) append(" · bağlanıyor…")
                        },
                        fontFamily = TerminalFont,
                        fontSize = 10.sp,
                        color = ConsoleDim,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    OverlayAction("Scrollback'te ara", { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(15.dp), tint = ConsoleDim)
                    }
                    OverlayAction("Scrollback'i paylaş", {
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
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = ConsoleDim)
                    }
                    OverlayAction("Tam ekran", { fullscreen = true }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = null, modifier = Modifier.size(15.dp), tint = ConsoleDim)
                    }
                    if (state == ConnectionState.CLOSED || state == ConnectionState.FAILED) {
                        if (controller.canReconnect()) {
                            OverlayAction("Yeniden bağlan", { controller.reconnect() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = TermGreen)
                            }
                        }
                    }
                    OverlayAction("Oturumu kapat", onClose) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(15.dp), tint = TermRed)
                    }
                }
            }

            // Hata banner'ı (overlay)
            if (state == ConnectionState.FAILED && failure != null) {
                Surface(
                    color = Color(0xE6140607),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TermRed.copy(alpha = 0.5f)),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).fillMaxWidth(0.94f),
                ) {
                    Text(
                        failureText(failure!!),
                        color = TermRed,
                        fontFamily = TerminalFont,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            // Arama çubuğu (overlay)
            if (searchOpen) {
                Surface(
                    color = Color(0xF210151D),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).fillMaxWidth(0.94f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                        TextField(
                            value = query,
                            onValueChange = { query = it; matchCursor = 0 },
                            placeholder = {
                                Text("Scrollback'te ara…", fontFamily = TerminalFont, fontSize = 12.sp, color = ConsoleDim)
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = TerminalFont, fontSize = 12.sp, color = TermText,
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (matches.isEmpty()) "0" else "${matchCursor + 1}/${matches.size}",
                            fontFamily = TerminalFont,
                            fontSize = 11.sp,
                            color = ConsoleDim,
                        )
                        IconButton(
                            enabled = matches.isNotEmpty(),
                            onClick = {
                                matchCursor = (matchCursor + 1) % matches.size
                                scope.launch { listState.scrollToItem(matches[matchCursor]) }
                            },
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Sonraki eşleşme",
                                tint = if (matches.isNotEmpty()) TermGreen else ConsoleDim,
                            )
                        }
                    }
                }
            }

            // Alta-in FAB
            if (!atBottom && lines.size > 1) {
                Surface(
                    color = Color(0xE6171E29),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                ) {
                    IconButton(onClick = { scope.launch { listState.scrollToItem(lines.size - 1) } }) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "En alta in",
                            tint = TermGreen,
                        )
                    }
                }
            }
            if (fullscreen) {
                Surface(
                    color = Color(0xE6171E29),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ConsoleBorder),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    IconButton(onClick = { fullscreen = false }) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Tam ekrandan çık", tint = ConsoleDim)
                    }
                }
            }
        }

        // ── Ghost tuşlar (tam ekranda gizli) ───────────────────────────────
        if (!fullscreen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostKey("esc") { sendText("\u001B") }
                GhostKey("tab") { sendText("\t") }
                GhostKey("ctrl", active = ctrl) { ctrl = !ctrl }
                GhostKey("^c") { sendText("\u0003") }
                GhostKey("^d") { sendText("\u0004") }
                GhostKey("^z") { sendText("\u001A") }
                GhostKey("←") { sendText("\u001B[D") }
                GhostKey("↓") { sendText("\u001B[B") }
                GhostKey("↑") { sendText("\u001B[A") }
                GhostKey("→") { sendText("\u001B[C") }
                GhostKey("home") { sendText("\u001B[H") }
                GhostKey("end") { sendText("\u001B[F") }
                GhostKey("pgup") { sendText("\u001B[5~") }
                GhostKey("pgdn") { sendText("\u001B[6~") }
                GhostKey("|") { sendText("|") }
                GhostKey("~") { sendText("~") }
                GhostKey("-") { sendText("-") }
                GhostKey("_") { sendText("_") }
            }
        }

        // ── Giriş çubuğu: ❯ prompt + borderless alan + aksiyonlar ──────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "❯",
                fontFamily = TerminalFont,
                fontSize = 14.sp,
                color = if (state == ConnectionState.ACTIVE) TermGreen else ConsoleDim,
                modifier = Modifier.padding(start = 4.dp),
            )
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        if (ctrl) "ctrl aktif — harf yaz" else "komut yaz…",
                        fontFamily = TerminalFont,
                        fontSize = 13.sp,
                        color = ConsoleDim,
                    )
                },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Terminal girişi" },
                singleLine = true,
                enabled = state == ConnectionState.ACTIVE,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = TerminalFont,
                    fontSize = 13.sp,
                    color = TermText,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = TermGreen,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendInput() }),
            )
            IconButton(
                onClick = { vm.historyOlder()?.let { text = it } },
                enabled = state == ConnectionState.ACTIVE,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp, contentDescription = "Önceki komut",
                    modifier = Modifier.size(18.dp),
                    tint = if (state == ConnectionState.ACTIVE) ConsoleDim else ConsoleDim.copy(alpha = 0.4f),
                )
            }
            IconButton(
                onClick = { text = vm.historyNewer() },
                enabled = state == ConnectionState.ACTIVE,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, contentDescription = "Sonraki komut",
                    modifier = Modifier.size(18.dp),
                    tint = if (state == ConnectionState.ACTIVE) ConsoleDim else ConsoleDim.copy(alpha = 0.4f),
                )
            }
            IconButton(
                onClick = {
                    val clip = clipboard.getText()?.text ?: return@IconButton
                    if (clip.contains('\n')) {
                        // Çok satırlı yapıştırma: bracketed paste açıksa kabuk ÇALIŞTIRMAZ.
                        val wrapped = if (vm.bracketedPaste) "\u001B[200~$clip\u001B[201~" else clip
                        sendText(wrapped)
                    } else {
                        text += clip
                    }
                },
                enabled = state == ConnectionState.ACTIVE,
                modifier = Modifier.size(32.dp).semantics { contentDescription = "Yapıştır" },
            ) {
                Icon(
                    Icons.Filled.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (state == ConnectionState.ACTIVE) ConsoleDim else ConsoleDim.copy(alpha = 0.4f),
                )
            }
            IconButton(
                onClick = { sendInput() },
                enabled = state == ConnectionState.ACTIVE && text.isNotEmpty(),
                modifier = Modifier.size(32.dp).semantics { contentDescription = "Gönder" },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (state == ConnectionState.ACTIVE && text.isNotEmpty()) TermGreen else ConsoleDim.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// Yarı saydam overlay ikonu (terminal içeriğinin üstünde).
@Composable
private fun OverlayAction(desc: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(32.dp)
            .semantics { contentDescription = desc },
        colors = IconButtonDefaults.iconButtonColors(contentColor = ConsoleDim),
    ) { icon() }
}

// Ghost tuş: çerçevesiz mono metin; ctrl aktifken yeşil vurgu.
@Composable
private fun GhostKey(label: String, active: Boolean = false, onTap: () -> Unit) {
    Text(
        label,
        fontFamily = TerminalFont,
        fontSize = 12.sp,
        color = if (active) TermGreen else ConsoleDim,
        modifier = Modifier
            .clickable(onClick = onTap)
            .background(
                if (active) TermGreen.copy(alpha = 0.12f) else Color.Transparent,
                MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

private fun failureText(f: TransportFailure): String = when (f) {
    is TransportFailure.AuthFailed -> "kimlik doğrulama reddedildi — parolayı/anahtarı kontrol et (fallback yok)"
    is TransportFailure.HostKeyChanged -> "HOST ANAHTARI DEĞİŞTİ — olası MITM, bağlantı durduruldu; revoke + yeniden eşle"
    is TransportFailure.Network -> "ağ hatası: ${f.reason}"
    is TransportFailure.MissingServer -> "sunucu bileşeni eksik: ${f.what}"
}

fun defaultTerminalSize(): TerminalSize = TerminalSize(80, 24)
