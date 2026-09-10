// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import dev.pocketagent.ui.theme.LocalMonoFont
import dev.pocketagent.ui.theme.LocalConsoleTheme
import kotlinx.coroutines.launch

// Görünmez IME yakalayıcı: alan kontrollü tutulur, her değişim PTY'ye
// delta (eklenen/silinen karakter) olarak akar.
private const val IME_MAX = 512

// IME alanı değişimini PTY girdisine çeviren saf fonksiyon. Görünmez alanın
// tamponu ile IME'nin tamponu desenkron olmasın diye alan kontrollü tutulur.
internal sealed interface ImeEdit {
    data class Insert(val text: String) : ImeEdit
    data class Delete(val count: Int) : ImeEdit
    data object None : ImeEdit
}

internal fun imeEdit(prev: String, next: String): ImeEdit = when {
    next == prev -> ImeEdit.None
    next.length > prev.length -> ImeEdit.Insert(next.substring(prev.length))
    next.length < prev.length -> ImeEdit.Delete(prev.length - next.length)
    else -> {
        // Aynı uzunlukta değişim (beklenmez; imleç daima sonda): değişen kuyruğu gönder.
        var i = 0
        while (i < next.length && next[i] == prev[i]) i++
        ImeEdit.Insert(next.substring(i))
    }
}

fun TermLine.toAnnotatedString(cursorCol: Int = -1, cursorBg: Color = Color.Unspecified, ansi: LongArray? = null): AnnotatedString = buildAnnotatedString {
    // ANSI indeksi varsa rengi aktif temanın paletinden çöz; truecolor doğrudan.
    fun resolve(c: Long?, idx: Int?): Color? =
        if (idx != null && ansi != null && idx in ansi.indices) Color(ansi[idx]) else c?.let { Color(it) }
    var pos = 0
    spans.forEach { s ->
        val spanStyle = SpanStyle(
            color = resolve(s.style.fg, s.style.fgIndex)
                ?: if (s.style.link != null) Color(0xFF5FA8F5) else Color.Unspecified,
            background = resolve(s.style.bg, s.style.bgIndex) ?: Color.Unspecified,
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
        // Sondaki boşluk hücreleri build()'de kırpılır; imleç gerçek
        // sütununa kadar boşluk doldurulmazsa son non-space karakterin
        // dibinde sabitlenir — echo'lanan boşluklar görünmez kalır.
        if (cursorCol > pos) append(" ".repeat(cursorCol - pos))
        withStyle(SpanStyle(background = cursorBg)) { append(' ') }
    }
}

@Composable
fun TerminalScreen(
    manager: SessionManager,
    settings: SettingsViewModel,
    onNewConnection: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = {},
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
            IconButton(onClick = onNewConnection, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Yeni bağlantı",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                onFullscreenChange = onFullscreenChange,
            )
        }
    }
}

// Oturum pill'i: ● ad (durum rengi), aktifte yeşil border; retry rozeti inline.
@Composable
private fun SessionPill(name: String, state: ConnectionState, retry: Int, active: Boolean, onClick: () -> Unit) {
    val borderColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateDot(state)
            Spacer(Modifier.width(6.dp))
            Text(
                if (retry > 0) "$name ↻$retry/${TerminalController.MAX_RETRY}" else name,
                fontFamily = LocalMonoFont.current,
                fontSize = 11.sp,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.primary,
            fontFamily = LocalMonoFont.current,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Bağlantılar sekmesinden bir host seç ya da QR ile eşle. Her host kendi oturumuyla açılır; yukarıdaki şeritle aralarında gezinebilirsin.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.clickable(onClick = onNewConnection),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("❯ host ekle", fontFamily = LocalMonoFont.current, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ActiveTerminal(
    controller: TerminalController,
    settings: SettingsViewModel,
    onClose: () -> Unit,
    onNewConnection: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val vm = controller.vm
    val lines by vm.lines.collectAsState()
    val cursor by vm.cursor.collectAsState()
    val windowTitle by vm.windowTitle.collectAsState()
    val pendingClip by vm.pendingClipboard.collectAsState()
    val state by controller.state.collectAsState()
    val failure by controller.failure.collectAsState()
    val active by controller.connectedTo.collectAsState()
    var ctrl by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    // Tam ekran durumunu üst katmana bildir — PocketApp nav bar'ı gizler,
    // yerine terminal kendi tuş şeridini gösterir.
    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }
    // Geri tuşu: önce aramayı kapat, sonra tam ekrandan çık.
    BackHandler(enabled = searchOpen || fullscreen) {
        if (searchOpen) { searchOpen = false; query = "" } else fullscreen = false
    }
    // Görünmez IME alanı: alanın gerçek içeriği (delta hesabı için).
    var imeBuf by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    // Viewport her değiştiğinde (klavye aç/kapa, tam ekran) artar; aktif satır
    // görünür kalsın diye alta kaydırmayı tetikler.
    var viewportEpoch by remember { mutableIntStateOf(0) }
    val inputFocus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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

    // BEL (\u0007): uzak zil → hafif haptic. Sayaç değişimi tetikler.
    val bellCount by vm.bellCount.collectAsState()
    LaunchedEffect(bellCount) {
        if (bellCount > 0) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

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
    // Klavye açılıp viewport küçülünce yazılan satır görünür kalsın.
    LaunchedEffect(viewportEpoch) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    val matches = remember(lines, query) {
        if (query.length < 2) emptyList()
        else lines.mapIndexedNotNull { i, l -> if (l.text.contains(query, ignoreCase = true)) i else null }
    }
    val matchSet = remember(matches) { matches.toSet() }
    var matchCursor by remember { mutableIntStateOf(0) }
    val currentMatch = matches.getOrNull(matchCursor)

    fun sendText(s: String) {
        controller.send(TerminalInput.Text(s))
    }

    // Yazılanı doğrudan PTY'ye gönder. ctrl mandalı açıkken tek harf kontrol
    // koduna dönüşür; çok satırlı girdi bracketed paste ile sarılır.
    fun emit(s: String) {
        if (s.isEmpty()) return
        if (ctrl && s.length == 1) {
            val c = s[0]
            if (c.code in 0x20..0x7E) {
                sendText(((c.uppercaseChar().code) and 0x1F).toChar().toString())
                ctrl = false
                return
            }
        }
        if (s.contains('\n')) {
            val wrapped = if (vm.bracketedPaste) "\u001B[200~$s\u001B[201~" else s
            sendText(wrapped)
        } else {
            sendText(s)
        }
    }

    // IME alanı değişimi: eklenen metin → tuş vuruşu, silinen karakter → backspace.
    // Alan kontrollü tutulduğu için IME tamponu ile desenkron olmaz.
    fun onImeChange(next: String) {
        when (val e = imeEdit(imeBuf, next)) {
            is ImeEdit.Insert -> emit(e.text)
            is ImeEdit.Delete -> repeat(e.count) { sendText("\u007F") }
            ImeEdit.None -> {}
        }
        imeBuf = if (next.length > IME_MAX) "" else next
    }

    Column(Modifier.fillMaxSize()) {
        val console = LocalConsoleTheme.current
        val termBg = Color(console.term.background)
        val termFg = Color(console.term.foreground)
        val density = LocalDensity.current
        val charW = with(density) { (13 * settings.theme.fontScale).sp.toPx() } * 0.6f
        val lineH = with(density) { (16 * settings.theme.fontScale).sp.toPx() }
        val connected = state == ConnectionState.ACTIVE

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = if (fullscreen) 0.dp else 6.dp),
        ) {
            Surface(
                color = termBg,
                shape = if (fullscreen) RoundedCornerShape(0.dp) else MaterialTheme.shapes.small,
                border = if (fullscreen) null
                else BorderStroke(1.dp, if (typing) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    // ── Çıktı alanı: dokun → doğrudan yaz (ayrı giriş satırı yok) ──
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .semantics { contentDescription = "Terminal çıktısı" }
                            .onSizeChanged { sz ->
                                val pad = with(density) { 16.dp.toPx() }
                                val cols = ((sz.width - pad) / charW).toInt().coerceIn(20, 500)
                                val rows = (sz.height / lineH).toInt().coerceIn(4, 200)
                                val newSize = TerminalSize(cols, rows)
                                if (newSize != vm.size) {
                                    vm.setSize(newSize)
                                    if (connected) controller.send(TerminalInput.Resize(newSize))
                                }
                                viewportEpoch++
                            }
                            .then(
                                if (connected) Modifier.pointerInput(Unit) {
                                    detectTapGestures {
                                        inputFocus.requestFocus()
                                        keyboard?.show()
                                    }
                                } else Modifier,
                            )
                            // Pinch-to-zoom: iki parmak fontScale'i anlık
                            // büyütür/küçültür → viewport→PTY resize zinciri
                            // zaten ölçümü takip eder.
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    if (zoom != 1f) {
                                        settings.setFontScale(settings.theme.fontScale * zoom)
                                    }
                                }
                            },
                    ) {
                        // Görünmez yakalayıcı: IME + donanım klavyesi buraya akar.
                        BasicTextField(
                            value = imeBuf,
                            onValueChange = { onImeChange(it) },
                            enabled = connected,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                            cursorBrush = SolidColor(Color.Transparent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Send,
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                            ),
                            keyboardActions = KeyboardActions(onSend = { sendText("\r") }),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .size(1.dp)
                                .focusRequester(inputFocus)
                                .onFocusChanged { typing = it.isFocused }
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    if (!connected) return@onPreviewKeyEvent false
                                    val k = ev.key
                                    when {
                                        ev.isCtrlPressed && k.keyCode in Key.A.keyCode..Key.Z.keyCode -> {
                                            val letter = 'a'.code + (k.keyCode - Key.A.keyCode).toInt()
                                            sendText(((letter and 0x1F).toChar()).toString()); true
                                        }
                                        k == Key.Enter || k == Key.NumPadEnter -> { sendText("\r"); true }
                                        k == Key.Backspace -> { sendText("\u007F"); true }
                                        k == Key.Tab -> { sendText("\t"); true }
                                        k == Key.DirectionUp -> { sendText("\u001B[A"); true }
                                        k == Key.DirectionDown -> { sendText("\u001B[B"); true }
                                        k == Key.DirectionRight -> { sendText("\u001B[C"); true }
                                        k == Key.DirectionLeft -> { sendText("\u001B[D"); true }
                                        k == Key.MoveHome -> { sendText("\u001B[H"); true }
                                        k == Key.MoveEnd -> { sendText("\u001B[F"); true }
                                        k == Key.PageUp -> { sendText("\u001B[5~"); true }
                                        k == Key.PageDown -> { sendText("\u001B[6~"); true }
                                        k == Key.Escape -> { sendText("\u001B"); true }
                                        else -> false
                                    }
                                },
                        )

                        if (lines.size <= 1 && lines.firstOrNull()?.text?.isBlank() != false) {
                            Column(
                                Modifier.fillMaxSize().padding(20.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    when (state) {
                                        ConnectionState.CONNECTING -> "$ bağlanıyor…"
                                        ConnectionState.ACTIVE -> "$ yazmaya başla"
                                        ConnectionState.FAILED -> "$ bağlantı hatası"
                                        else -> "$ bekleniyor"
                                    },
                                    color = when (state) {
                                        ConnectionState.CONNECTING -> TermAmber
                                        ConnectionState.FAILED -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    fontFamily = LocalMonoFont.current,
                                    fontSize = (14 * settings.theme.fontScale).sp,
                                )
                            }
                        } else {
                            SelectionContainer {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    // Üstteki yarı saydam aksiyon çubuğu çıktının ilk
                                    // satırlarını gizlemesin diye üst boşluk.
                                    contentPadding = PaddingValues(
                                        start = 8.dp,
                                        end = 8.dp,
                                        bottom = 8.dp,
                                        top = 40.dp,
                                    ),
                                ) {
                                    itemsIndexed(lines) { idx, line ->
                                        val isMatch = currentMatch == idx
                                        val hasMatch = matchSet.contains(idx)
                                        val cur = cursor
                                        val cursorCol = if (cur != null && cur.first == idx) cur.second else -1
                                        Text(
                                            line.toAnnotatedString(cursorCol, Color(console.term.cursor), console.term.ansi),
                                            color = termFg,
                                            fontFamily = LocalMonoFont.current,
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

                        // Alta-in FAB (çıktı alanı içinde; tuş şeridiyle çakışmaz)
                        if (!atBottom && lines.size > 1) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                            ) {
                                IconButton(onClick = { scope.launch { listState.scrollToItem(lines.size - 1) } }) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "En alta in",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    // ── Alt tuş şeridi: terminal yüzeyine bitişik, ince ayırıcı ──
                    // Tam ekranda da gösterilir — Scaffold nav bar'ının yerini alır.
                    if (!fullscreen) ConsoleDivider()
                    TerminalKeyBar(
                        ctrl = ctrl,
                        enabled = connected,
                        typing = typing,
                        snippets = settings.snippetList(),
                        onCtrl = { ctrl = !ctrl },
                        onKey = { sendText(it) },
                        onPaste = {
                            val clip = clipboard.getText()?.text ?: ""
                            if (clip.isNotEmpty()) emit(clip)
                        },
                        onKeyboard = {
                            // clearFocus, sistem geri tuşuyla kapatılmış IME'de de
                            // durumu sıfırlar (hide() no-op kalıyordu).
                            if (typing) focusManager.clearFocus()
                            else { inputFocus.requestFocus(); keyboard?.show() }
                        },
                    )
                }
            }

            // ── Overlay: üst-sağ aksiyon çubuğu (yarı saydam, içeriğin üstünde) ──
            // Tam ekranda da görünür — ara/paylaş/kapat erişilebilir kalır.
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f), MaterialTheme.shapes.extraSmall),
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
                    fontFamily = LocalMonoFont.current,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
                OverlayAction("Scrollback'te ara", { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OverlayAction(
                    if (fullscreen) "Tam ekrandan çık" else "Tam ekran",
                    { fullscreen = !fullscreen },
                ) {
                    Icon(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state == ConnectionState.CLOSED || state == ConnectionState.FAILED) {
                    if (controller.canReconnect()) {
                        OverlayAction("Yeniden bağlan", { controller.reconnect() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                OverlayAction("Oturumu kapat", onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                }
            }

            // Hata banner'ı (overlay): mesaj + yapılabilir aksiyon.
            if (state == ConnectionState.FAILED && failure != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).fillMaxWidth(0.94f),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            failureText(failure!!),
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = LocalMonoFont.current,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (controller.canReconnect()) {
                                TextButton(onClick = { controller.reconnect() }) {
                                    Text("Yeniden dene", fontSize = 11.sp)
                                }
                            }
                            TextButton(onClick = onNewConnection) {
                                Text("Bağlantıya git", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Arama çubuğu (overlay)
            if (searchOpen) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).fillMaxWidth(0.94f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                        TextField(
                            value = query,
                            onValueChange = { query = it; matchCursor = 0 },
                            placeholder = {
                                Text("Scrollback'te ara…", fontFamily = LocalMonoFont.current, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = LocalMonoFont.current, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
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
                            fontFamily = LocalMonoFont.current,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(
                            enabled = matches.isNotEmpty(),
                            onClick = {
                                matchCursor = (matchCursor - 1 + matches.size) % matches.size
                                scope.launch { listState.scrollToItem(matches[matchCursor]) }
                            },
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Önceki eşleşme",
                                tint = if (matches.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                                tint = if (matches.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

        }
    }
}

// Alt tuş şeridi: solda ctrl mandalı, ortada kaydırılabilir tuşlar, sağda
// yapıştır/klavye. Terminal yüzeyinin bir parçası; ayrı bir giriş satırı yok.
@Composable
private fun TerminalKeyBar(
    ctrl: Boolean,
    enabled: Boolean,
    typing: Boolean,
    snippets: List<Pair<String, String>> = emptyList(),
    onCtrl: () -> Unit,
    onKey: (String) -> Unit,
    onPaste: () -> Unit,
    onKeyboard: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TermKey("ctrl", enabled = enabled, active = ctrl, onTap = onCtrl)
        TermKeyDivider()
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TermKey("esc", enabled) { onKey("\u001B") }
            TermKey("tab", enabled) { onKey("\t") }
            TermKey("^c", enabled) { onKey("\u0003") }
            TermKey("^d", enabled) { onKey("\u0004") }
            TermKey("^z", enabled) { onKey("\u001A") }
            TermKey("^l", enabled) { onKey("\u000C") }
            TermKeyDivider()
            TermKey("←", enabled) { onKey("\u001B[D") }
            TermKey("↑", enabled) { onKey("\u001B[A") }
            TermKey("↓", enabled) { onKey("\u001B[B") }
            TermKey("→", enabled) { onKey("\u001B[C") }
            TermKeyDivider()
            TermKey("home", enabled) { onKey("\u001B[H") }
            TermKey("end", enabled) { onKey("\u001B[F") }
            TermKey("pgup", enabled) { onKey("\u001B[5~") }
            TermKey("pgdn", enabled) { onKey("\u001B[6~") }
            TermKeyDivider()
            TermKey("|", enabled) { onKey("|") }
            TermKey("~", enabled) { onKey("~") }
            TermKey("-", enabled) { onKey("-") }
            TermKey("_", enabled) { onKey("_") }
            TermKey("/", enabled) { onKey("/") }
            // Kullanıcı snippet'ları (Ayarlar → Tuş şeridi): etiket basılır,
            // komut metni gönderilir (Enter kullanıcıda — iptal şansı kalır).
            if (snippets.isNotEmpty()) {
                TermKeyDivider()
                snippets.forEach { (label, cmd) ->
                    TermKey(label, enabled) { onKey(cmd) }
                }
            }
        }
        TermKeyDivider()
        TermIconKey(
            icon = if (typing) Icons.Filled.KeyboardHide else Icons.Filled.Keyboard,
            desc = if (typing) "Klavyeyi kapat" else "Klavyeyi aç",
            enabled = enabled,
            onTap = onKeyboard,
        )
        TermIconKey(Icons.Filled.ContentPaste, "Yapıştır", enabled, onPaste)
    }
}

// Konsol tuşu: çerçevesiz mono metin, basılıyken hafif zemin + hafif haptic;
// ripple yok. Haptic, dokunmanın algılandığını garantiler (tuşlar ripple'sız).
@Composable
private fun TermKey(
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onTap: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    Box(
        Modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(
                when {
                    active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    pressed && enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onTap()
                },
            )
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = LocalMonoFont.current,
            fontSize = 13.sp,
            maxLines = 1,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun TermIconKey(icon: ImageVector, desc: String, enabled: Boolean, onTap: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    Box(
        Modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (pressed && enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onTap()
                },
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = desc,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

// Tuş grupları arasında ince dikey ayırıcı.
@Composable
private fun TermKeyDivider() {
    Box(
        Modifier
            .padding(horizontal = 5.dp)
            .width(1.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

// Yarı saydam overlay ikonu (terminal içeriğinin üstünde).
@Composable
private fun OverlayAction(desc: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(32.dp)
            .semantics { contentDescription = desc },
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) { icon() }
}

private fun failureText(f: TransportFailure): String = when (f) {
    is TransportFailure.AuthFailed -> "kimlik doğrulama reddedildi — parolayı/anahtarı kontrol et (fallback yok)"
    is TransportFailure.HostKeyChanged -> "HOST ANAHTARI DEĞİŞTİ — olası MITM, bağlantı durduruldu; revoke + yeniden eşle"
    is TransportFailure.Network -> "ağ hatası: ${f.reason}"
    is TransportFailure.MissingServer -> "sunucu bileşeni eksik: ${f.what}"
}

fun defaultTerminalSize(): TerminalSize = TerminalSize(80, 24)
