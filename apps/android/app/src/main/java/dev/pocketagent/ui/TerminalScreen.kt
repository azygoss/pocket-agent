// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SessionHandle
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.TerminalController
import dev.pocketagent.transport.TermLine
import dev.pocketagent.transport.TerminalInput
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TransportFailure
import dev.pocketagent.ui.theme.TermAmber
import dev.pocketagent.ui.theme.LocalMonoFont
import dev.pocketagent.ui.theme.LocalConsoleTheme
import kotlin.math.roundToInt
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

// Terminal chrome durumu: arama/tam-ekran bayrakları üst bar (PocketApp)
// ile panel arasında paylaşılır — aksiyonlar uygulamanın üst barında yaşar.
class TerminalChromeState {
    var searchOpen by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
}

// Scrollback'i metin dosyasına döküp paylaş — panel ve üst bar aynı yolu kullanır.
internal fun shareScrollback(context: android.content.Context, lines: List<TermLine>) {
    val dump = lines.joinToString("\n") { it.text }
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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
}

@Composable
fun TerminalScreen(
    manager: SessionManager,
    settings: SettingsViewModel,
    onNewConnection: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = {},
    onCollapse: () -> Unit = {},
    chrome: TerminalChromeState = remember { TerminalChromeState() },
) {
    val sessionList by manager.sessions.collectAsState()
    val activeId by manager.activeId.collectAsState()

    Column(Modifier.fillMaxSize().imePadding()) {
        val active = sessionList.firstOrNull { it.id == activeId }
        if (active == null) {
            // Oturum yokken şerit burada kalır (yeni bağlantı + geçiş).
            SessionPillsRow(
                manager = manager,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                onNewConnection = onNewConnection,
            )
            EmptyTerminal(onNewConnection)
        } else {
            ActiveTerminal(
                handle = active,
                controller = active.controller,
                manager = manager,
                settings = settings,
                onClose = { manager.close(active.id) },
                onNewConnection = onNewConnection,
                onFullscreenChange = onFullscreenChange,
                onCollapse = onCollapse,
                chrome = chrome,
            )
        }
    }
}

// Oturum şeridi: durum entegre mono pill'ler + yeni-bağlantı düğmesi.
// Kaydırılabilir — çağıran weight/padding'i belirler.
@Composable
private fun SessionPillsRow(
    manager: SessionManager,
    modifier: Modifier = Modifier,
    onNewConnection: () -> Unit,
) {
    val sessionList by manager.sessions.collectAsState()
    val activeId by manager.activeId.collectAsState()
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sessionList.forEachIndexed { idx, h ->
            val st by h.controller.state.collectAsState()
            val retry by h.controller.retryAttempt.collectAsState()
            // Aynı host'ta paralel oturumlar: ·2, ·3… ile ayırt et.
            val same = sessionList.count { it.conn.id == h.conn.id }
            val nth = sessionList.take(idx).count { it.conn.id == h.conn.id }
            val label = if (same > 1) "${h.conn.name}·${nth + 1}" else h.conn.name
            SessionPill(
                name = label,
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
}

// Oturum sekmesi: editor-tab dili — aktif sekme üst köşeleri yuvarlak, zemini
// terminal yüzeyiyle aynı renk; aşağı doğru panele "bağlanır". Durum noktası +
// ad + retry `↻n/5`.
@Composable
private fun SessionPill(name: String, state: ConnectionState, retry: Int, active: Boolean, onClick: () -> Unit) {
    val fg = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StateDot(state, size = 6.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            name,
            fontFamily = LocalMonoFont.current,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            maxLines = 1,
        )
        if (retry > 0) {
            Text(
                " ↻$retry/${TerminalController.MAX_RETRY}",
                fontFamily = LocalMonoFont.current,
                fontSize = 10.5.sp,
                color = fg.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyTerminal(onNewConnection: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Filled.Terminal,
            title = "açık oturum yok",
            body = "Bağlantılar sekmesinden bir host seç ya da QR ile eşle. Her host kendi oturumuyla açılır; yukarıdaki şeritle aralarında gezinebilirsin.",
        ) {
            ConsoleButton(onClick = onNewConnection) { Text("Host ekle") }
        }
    }
}

@Composable
private fun ActiveTerminal(
    handle: SessionHandle,
    controller: TerminalController,
    manager: SessionManager,
    settings: SettingsViewModel,
    onClose: () -> Unit,
    onNewConnection: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = {},
    onCollapse: () -> Unit = {},
    chrome: TerminalChromeState = remember { TerminalChromeState() },
) {
    val vm = controller.vm
    val lines by vm.lines.collectAsState()
    val cursor by vm.cursor.collectAsState()
    val windowTitle by vm.windowTitle.collectAsState()
    val pendingClip by vm.pendingClipboard.collectAsState()
    val state by controller.state.collectAsState()
    val failure by controller.failure.collectAsState()
    var ctrl by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Arama/tam-ekran bayrakları paylaşılan chrome durumunda — üst bardan
    // (PocketApp) tetiklenir; tam ekranda üst bar gizlendiği için çıkış
    // aksiyonu panel başlığında kalır.
    val searchOpen = chrome.searchOpen
    val fullscreen = chrome.fullscreen
    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }
    // Üst bar aramayı kapattığında sorgu da temizlenir.
    LaunchedEffect(searchOpen) { if (!searchOpen) query = "" }
    // Geri tuşu: önce aramayı kapat, sonra tam ekrandan çık.
    BackHandler(enabled = searchOpen || fullscreen) {
        if (searchOpen) { chrome.searchOpen = false } else chrome.fullscreen = false
    }
    // Görünmez IME alanı: alanın gerçek içeriği (delta hesabı için).
    var imeBuf by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    // Viewport her değiştiğinde (klavye aç/kapa, tam ekran) artar; aktif satır
    // görünür kalsın diye alta kaydırmayı tetikler.
    var viewportEpoch by remember { mutableIntStateOf(0) }
    // Klavye aç/kapa animasyonu onSizeChanged'i her frame'de tetikler —
    // her karede PTY'ye Resize göndermek uzak tarafı SIGWINCH yağmuruna
    // tutar (prompt defalarca yeniden basılır). Gönderim debounce'lu.
    var resizeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
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

    // Gerçek terminal düzeni: içerik üstten büyür, ekran dolana kadar
    // prompt üstte durur; dolunca imleç satırı viewport'un altında tutulur.
    // visRows = görünen satır sayısı (klavye açıkken kısalır).
    var visRows by remember { mutableIntStateOf(24) }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 1
        }
    }
    // İmleç satırı görünürde kalsın: fold'un üstündeyse üste, altındaysa
    // alta hizala; zaten görünüyorsa hiç dokunma (kayma yok).
    suspend fun revealCursor() {
        if (lines.isEmpty()) return
        val target = (cursor?.first ?: lines.size - 1).coerceIn(0, lines.size - 1)
        val vis = listState.layoutInfo.visibleItemsInfo
        if (vis.isEmpty()) {
            listState.scrollToItem((target - visRows + 1).coerceAtLeast(0))
            return
        }
        if (target < vis.first().index) listState.scrollToItem(target)
        else if (target > vis.last().index) {
            listState.scrollToItem((target - visRows + 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && atBottom) revealCursor()
    }
    // Klavye açılıp viewport küçülünce yazılan satır görünür kalsın.
    LaunchedEffect(viewportEpoch) {
        if (lines.isNotEmpty()) revealCursor()
    }

    // Alt-screen açıldığında (vim/htop/less/tmux) bekleyen gerçek boyutu
    // uzak tarafa ilet — satır-only resize'lar düz shell'de filtrelendiği
    // için uzak tarafın satır sayısı eski kalmış olabilir.
    val altActive by vm.altScreen.collectAsState()
    LaunchedEffect(altActive) {
        if (altActive) controller.send(TerminalInput.Resize(vm.size))
    }

    // Bağlantı ACTIVE'a geçince gerçek boyutu bir kez gönder — CONNECTING
    // sırasında ölçülen boyut send()'de ACTIVE kapısına takılıyordu ve PTY
    // açılış tahmini (lastViewportSize) bayatsa uzak winsize kalıcı olarak
    // yerelden sapıyordu → TUI agent'lar ekrana sığmıyordu. Dedupe no-op.
    LaunchedEffect(state) {
        if (state == ConnectionState.ACTIVE) controller.send(TerminalInput.Resize(vm.size))
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
        // Çoklu oturumda geçiş şeridi panelin üstünde kalır; tek oturumda
        // panel header'ı adı taşır (referans düzen: sadece başlık + rozeti).
        val handles by manager.sessions.collectAsState()
        if (handles.size > 1) {
            SessionPillsRow(
                manager,
                Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, end = 2.dp),
                onNewConnection,
            )
        }

        val console = LocalConsoleTheme.current
        val termBg = Color(console.term.background)
        val termFg = Color(console.term.foreground)
        val density = LocalDensity.current
        // Hücre genişliği gerçek fontla ölçülür — `0.6em` kestirimi seçili
        // mono fonta göre saptığında remote cols yanlış açılır ve TUI
        // agent'lar ekrana sığmaz.
        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
        val monoFamily = LocalMonoFont.current
        val fontScale = settings.theme.fontScale
        val charW = remember(monoFamily, fontScale, density) {
            val m = textMeasurer.measure(
                AnnotatedString("0123456789"),
                style = TextStyle(fontFamily = monoFamily, fontSize = (13 * fontScale).sp),
            )
            (m.size.width / 10f).coerceAtLeast(1f)
        }
        val lineH = with(density) { (16 * fontScale).sp.toPx() }
        val connected = state == ConnectionState.ACTIVE
        val conn = handle.conn
        // Klavye açıkken panel-kapsül arasındaki hava boşluğu daralır —
        // kapsül klavyenin hemen üstünde durur, görüş alanı korunur.
        val imeInsets = WindowInsets.ime
        val imeOpen = imeInsets.getBottom(density) > 0
        // Tutamaçtan aşağı çekme: panel parmağı spring ile takip eder;
        // eşik altında bırakılırsa geri yaylanır, üstünde aşağı akıp kapanır.
        var pull by remember { mutableFloatStateOf(0f) }
        val pullY by animateFloatAsState(
            pull,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "pull",
        )
        val pullMax = with(density) { 320.dp.toPx() }
        val pullThreshold = with(density) { 56.dp.toPx() }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .offset { IntOffset(0, pullY.roundToInt()) }
                .graphicsLayer { alpha = 1f - (pullY / pullMax) * 0.3f }
                .then(
                    if (fullscreen) Modifier
                    else Modifier.padding(
                        start = 10.dp, end = 10.dp, top = 6.dp,
                        bottom = if (imeOpen) 6.dp else 10.dp,
                    ),
                ),
        ) {
            // Yüzen terminal paneli: tutamaç + başlık + çıktı tek yuvarlak
            // yüzeyde; tuş şeridi ayrı kapsül olarak altta yüzer.
            Surface(
                color = termBg,
                shape = if (fullscreen) RectangleShape else RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Üst bölge (tutamaç + başlık) aşağı çekilebilir: panel
                    // parmağı izler; eşik geçilirse aşağı akıp ana sayfadaki
                    // oturum kartlarına döner (oturum yaşar), geçilmezse geri
                    // yaylanır. Buton dokunuşları çalışmaya devam eder — sürükleme
                    // yalnız dikey kayma eşiği aşılınca olayı alır.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (pull > pullThreshold) {
                                            pull = pullMax
                                            scope.launch {
                                                kotlinx.coroutines.delay(140)
                                                onCollapse()
                                            }
                                        } else pull = 0f
                                    },
                                    onDragCancel = { pull = 0f },
                                ) { _, dy -> pull = (pull + dy).coerceIn(0f, pullMax) }
                            },
                    ) {
                        if (!fullscreen) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .width(34.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)),
                                )
                            }
                        }
                    // ── Panel başlığı: durum noktası + oturum + rozet + aksiyonlar ──
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StateDot(state, size = 7.dp)
                        Row(
                            Modifier.weight(1f).padding(start = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                conn.name,
                                fontFamily = LocalMonoFont.current,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                                maxLines = 1,
                            )
                            Text(
                                buildString {
                                    append("  ${conn.user}@${conn.host}")
                                    if (windowTitle.isNotBlank()) append(" · $windowTitle")
                                    if (state == ConnectionState.CONNECTING) append(" · bağlanıyor…")
                                },
                                fontFamily = LocalMonoFont.current,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        // Transport rozeti (SSH/MOSH) — mavi tonal kapsül.
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Color(console.accentAlt).copy(alpha = 0.16f))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Text(
                                vm.badge,
                                fontFamily = LocalMonoFont.current,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(console.accentAlt),
                            )
                        }
                        // Tam ekranda üst bar gizli — aksiyonlar panele döner.
                        if (fullscreen) {
                            OverlayAction("Scrollback'te ara", { chrome.searchOpen = !chrome.searchOpen }) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OverlayAction("Scrollback'i paylaş", { shareScrollback(context, lines) }) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OverlayAction("Tam ekrandan çık", { chrome.fullscreen = false }) {
                                Icon(Icons.Filled.FullscreenExit, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state == ConnectionState.CLOSED || state == ConnectionState.FAILED) {
                                if (controller.canReconnect()) {
                                    OverlayAction("Yeniden bağlan", { controller.reconnect() }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            // X: oturumu kapatır ve terminalden çıkar.
                            OverlayAction("Oturumu kapat", { onClose(); onCollapse() }) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    }
                    // ── Çıktı alanı: dokun → doğrudan yaz (ayrı giriş satırı yok) ──
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .semantics { contentDescription = "Terminal çıktısı" }
                            .onSizeChanged { sz ->
                                val pad = with(density) { 16.dp.toPx() }
                                val cols = ((sz.width - pad) / charW).toInt().coerceIn(20, 500)
                                // Satır sayısı IME'siz ölçülür: klavye açılınca
                                // görünen alan kısalır ama terminal modeli
                                // (ve remote PTY) değişmez — LazyColumn
                                // kırpar, scroll imleci izler. Aksi halde
                                // model küçülürken üst satırlar (prompt dahil)
                                // scrollback'e itilir ve ekran boş kalır.
                                val imePx = imeInsets.getBottom(density)
                                // Yuvarla (floor değil): piksel-sapmalı ölçümler
                                // sınırda ±1 satır üretmesin — gereksiz reflow'u
                                // ve scrollback itmesini önler.
                                val rows = ((sz.height + imePx) / lineH).roundToInt().coerceIn(4, 200)
                                val newSize = TerminalSize(cols, rows)
                                // Görünen satır sayısı (IME'li — kırpılan alan)
                                // revealCursor'un alta-hizala hesabı için.
                                visRows = (sz.height / lineH).toInt().coerceAtLeast(1)
                                resizeJob?.cancel()
                                resizeJob = scope.launch {
                                    kotlinx.coroutines.delay(160)
                                    if (newSize != vm.size) {
                                        vm.setSize(newSize)
                                        if (controller.state.value == ConnectionState.ACTIVE) {
                                            controller.send(TerminalInput.Resize(newSize))
                                        }
                                    }
                                }
                                // Görünen alan her değiştiğinde imleç satırı
                                // izlensin (scroll ucuz — model reflow'u yok).
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
                            // Boş çıktı: mono durum satırı + yanıp sönen imleç bloğu.
                            val cursorAlpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "cursor")
                                .animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.15f,
                                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                        androidx.compose.animation.core.tween(530),
                                        androidx.compose.animation.core.RepeatMode.Reverse,
                                    ),
                                    label = "cursor-alpha",
                                )
                            Column(
                                Modifier.fillMaxSize().padding(20.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when (state) {
                                            ConnectionState.CONNECTING -> "❯ bağlanıyor…"
                                            ConnectionState.ACTIVE -> "❯ yazmaya başla"
                                            ConnectionState.FAILED -> "❯ bağlantı hatası"
                                            else -> "❯ bekleniyor"
                                        },
                                        color = when (state) {
                                            ConnectionState.CONNECTING -> TermAmber
                                            ConnectionState.FAILED -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        fontFamily = LocalMonoFont.current,
                                        fontSize = (14 * settings.theme.fontScale).sp,
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Box(
                                        Modifier
                                            .width(8.dp)
                                            .height(16.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha)),
                                    )
                                }
                            }
                        } else {
                            SelectionContainer {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(8.dp),
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
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape,
                                shadowElevation = 6.dp,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                            ) {
                                IconButton(onClick = {
                                    scope.launch {
                                        listState.scrollToItem((lines.size - visRows).coerceAtLeast(0))
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "En alta in",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                }
            }

            // Hata banner'ı (overlay): mesaj + yapılabilir aksiyon.
            if (state == ConnectionState.FAILED && failure != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).fillMaxWidth(0.94f),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            failureText(failure!!),
                            color = MaterialTheme.colorScheme.onErrorContainer,
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 8.dp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).fillMaxWidth(0.94f),
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

        // ── Yüzen kapsül tuş şeridi: panelden ayrı durur, tam ekranda da ──
        // Scaffold nav bar'ının yerini alır.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = if (imeOpen) 4.dp else 8.dp),
        ) {
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
}

// Alt tuş şeridi: solda ctrl mandalı, ortada kaydırılabilir tuşlar, sağda
// yapıştır/klavye. Yüzen kapsülün içeriği; ayrı bir giriş satırı yok.
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
            .height(48.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TermKey("ctrl", enabled = enabled, active = ctrl, onTap = onCtrl)
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TermKey("esc", enabled) { onKey("\u001B") }
            TermKey("tab", enabled) { onKey("\t") }
            TermKey("^c", enabled) { onKey("\u0003") }
            TermKey("^d", enabled) { onKey("\u0004") }
            TermKey("^z", enabled) { onKey("\u001A") }
            TermKey("^l", enabled) { onKey("\u000C") }
            Spacer(Modifier.width(5.dp))
            TermKey("←", enabled) { onKey("\u001B[D") }
            TermKey("↑", enabled) { onKey("\u001B[A") }
            TermKey("↓", enabled) { onKey("\u001B[B") }
            TermKey("→", enabled) { onKey("\u001B[C") }
            Spacer(Modifier.width(5.dp))
            TermKey("home", enabled) { onKey("\u001B[H") }
            TermKey("end", enabled) { onKey("\u001B[F") }
            TermKey("pgup", enabled) { onKey("\u001B[5~") }
            TermKey("pgdn", enabled) { onKey("\u001B[6~") }
            Spacer(Modifier.width(5.dp))
            TermKey("|", enabled) { onKey("|") }
            TermKey("~", enabled) { onKey("~") }
            TermKey("-", enabled) { onKey("-") }
            TermKey("_", enabled) { onKey("_") }
            TermKey("/", enabled) { onKey("/") }
            // Kullanıcı snippet'ları (Ayarlar → Tuş şeridi): etiket basılır,
            // komut metni gönderilir (Enter kullanıcıda — iptal şansı kalır).
            snippets.forEach { (label, cmd) ->
                TermKey(label, enabled) { onKey(cmd) }
            }
        }
        Spacer(Modifier.width(6.dp))
        TermIconKey(
            icon = if (typing) Icons.Filled.KeyboardHide else Icons.Filled.Keyboard,
            desc = if (typing) "Klavyeyi kapat" else "Klavyeyi aç",
            enabled = enabled,
            onTap = onKeyboard,
        )
        TermIconKey(Icons.Filled.ContentPaste, "Yapıştır", enabled, onPaste)
    }
}

// Konsol tuşu: tonal keycap — yumuşak köşeli dolgu hücre; aktif mandal
// accent dolgu + onPrimary, basılıyken koyulaşır + haptic. Ripple yok —
// haptic dokunmanın algılandığını garantiler.
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
            .padding(vertical = 6.dp)
            .defaultMinSize(minWidth = 34.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                when {
                    active -> MaterialTheme.colorScheme.primary
                    pressed && enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
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
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = LocalMonoFont.current,
            fontSize = 12.sp,
            maxLines = 1,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                active -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
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
            .padding(vertical = 6.dp)
            .width(38.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (pressed && enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onTap()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = desc,
            modifier = Modifier.size(17.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

// Panel başlığı aksiyonu: dairesel tonal buton (referans chrome).
@Composable
private fun OverlayAction(desc: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .semantics { contentDescription = desc }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

private fun failureText(f: TransportFailure): String = when (f) {
    is TransportFailure.AuthFailed -> "kimlik doğrulama reddedildi — parolayı/anahtarı kontrol et (fallback yok)"
    is TransportFailure.HostKeyChanged -> "HOST ANAHTARI DEĞİŞTİ — olası MITM, bağlantı durduruldu; revoke + yeniden eşle"
    is TransportFailure.Network -> "ağ hatası: ${f.reason}"
    is TransportFailure.MissingServer -> "sunucu bileşeni eksik: ${f.what}"
}

fun defaultTerminalSize(): TerminalSize = TerminalSize(80, 24)
