// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.LocalForwarder
import dev.pocketagent.transport.PREVIEW_PROBE_CMD
import dev.pocketagent.transport.PreviewProbe
import dev.pocketagent.transport.PreviewTarget
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.SessionHandle
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.TcpipCapable
import dev.pocketagent.transport.TermLine
import dev.pocketagent.transport.TerminalController
import dev.pocketagent.transport.TerminalInput
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TransportFailure
import dev.pocketagent.transport.localPath
import dev.pocketagent.transport.parsePreviewProbe
import dev.pocketagent.transport.previewTargetsFromTexts
import dev.pocketagent.transport.previewTokenFromTexts
import dev.pocketagent.ui.theme.Code
import dev.pocketagent.ui.theme.KeyShape
import dev.pocketagent.ui.theme.LocalConsoleTheme
import dev.pocketagent.ui.theme.LocalMonoFont
import dev.pocketagent.ui.theme.PillShape
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
            fontStyle = if (s.style.italic) FontStyle.Italic else null,
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

// Terminal chrome durumu: arama/tam-ekran bayrakları kabuk (PocketApp) ile
// paylaşılır — kabuk ekrandan çıkınca ikisini sıfırlar.
class TerminalChromeState {
    var searchOpen by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
}

// Scrollback'i metin dosyasına döküp paylaş.
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

// Aynı host'ta paralel oturumlar "ad · 2" diye ayrılır; özel ad önceliklidir.
internal fun sessionLabel(h: SessionHandle, all: List<SessionHandle>, custom: Map<String, String>): String {
    custom[h.id]?.let { return it }
    val same = all.count { it.conn.id == h.conn.id }
    if (same <= 1) return h.conn.name
    val nth = all.takeWhile { it.id != h.id }.count { it.conn.id == h.conn.id }
    return "${h.conn.name} · ${nth + 1}"
}

// ── Çalışma alanı ───────────────────────────────────────────────────────────
// Kenardan kenara terminal: üstte oturum çubuğu (aşağı çekilince
// Oturumlar'a küçülür), birden çok oturumda sekmeler, altta yüzen tuş
// paneli. Dosyalar, önizleme ve paylaşım çubuğun eylemlerinde.

@Composable
fun TerminalScreen(
    manager: SessionManager,
    settings: SettingsViewModel,
    onNewConnection: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = {},
    onCollapse: () -> Unit = {},
    chrome: TerminalChromeState = remember { TerminalChromeState() },
    onOpenFiles: () -> Unit = {},
    repo: ConnectionRepository? = null,
    onEditHost: (SavedConnection) -> Unit = {},
    showBack: Boolean = true,
) {
    val sessionList by manager.sessions.collectAsState()
    val activeId by manager.activeId.collectAsState()
    val active = sessionList.firstOrNull { it.id == activeId }
    if (active == null) {
        EmptyWorkspace(onNewConnection = onNewConnection, onBack = if (showBack) onCollapse else null)
    } else {
        ActiveTerminal(
            handle = active,
            manager = manager,
            settings = settings,
            onNewConnection = onNewConnection,
            onFullscreenChange = onFullscreenChange,
            onCollapse = onCollapse,
            chrome = chrome,
            onOpenFiles = onOpenFiles,
            repo = repo,
            onEditHost = onEditHost,
            showBack = showBack,
        )
    }
}

@Composable
private fun EmptyWorkspace(onNewConnection: () -> Unit, onBack: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().background(Tok.bg)) {
        PocketTopBar("Terminal", onBack = onBack, backIcon = Icons.Rounded.ExpandMore, backDesc = "Oturumlara dön")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.Terminal,
                title = "Henüz açık oturum yok",
                body = "Bir host seç ya da QR ile eşle. Her oturum host'ta kendi tmux'unda yaşar; bağlantı koparsa kaldığı yerden devam eder.",
            ) {
                PrimaryButton("Yeni bağlantı", onClick = onNewConnection, icon = Icons.Rounded.Add)
            }
        }
    }
}

@Composable
private fun ActiveTerminal(
    handle: SessionHandle,
    manager: SessionManager,
    settings: SettingsViewModel,
    onNewConnection: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    chrome: TerminalChromeState,
    onOpenFiles: () -> Unit,
    repo: ConnectionRepository?,
    onEditHost: (SavedConnection) -> Unit,
    showBack: Boolean,
) {
    val controller = handle.controller
    val vm = controller.vm
    val lines by vm.lines.collectAsState()
    val cursor by vm.cursor.collectAsState()
    val pendingClip by vm.pendingClipboard.collectAsState()
    val state by controller.state.collectAsState()
    val retry by controller.retryAttempt.collectAsState()
    val failure by controller.failure.collectAsState()
    val conn by controller.connectedTo.collectAsState()
    val sessionList by manager.sessions.collectAsState()
    val customNames by manager.customNames.collectAsState()
    val tmuxNames by manager.tmuxNames.collectAsState()
    var ctrl by remember(handle.id) { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchOpen = chrome.searchOpen
    val fullscreen = chrome.fullscreen
    LaunchedEffect(fullscreen) { onFullscreenChange(fullscreen) }
    LaunchedEffect(searchOpen) { if (!searchOpen) query = "" }
    // Geri tuşu: önce aramayı kapat, sonra tam ekrandan çık.
    BackHandler(enabled = searchOpen || fullscreen) {
        if (searchOpen) chrome.searchOpen = false else chrome.fullscreen = false
    }
    // Görünmez IME alanı: alanın gerçek içeriği (delta hesabı için).
    var imeBuf by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    // Viewport her değiştiğinde (klavye aç/kapa, tam ekran) artar; aktif satır
    // görünür kalsın diye alta kaydırmayı tetikler.
    var viewportEpoch by remember { mutableIntStateOf(0) }
    // Terminal yüzeyinin son ölçüsü; boyut hesabı aşağıda debounce'lu efektte.
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    // Oturum çubuğunun (sekmeler dahil) son ölçülen yüksekliği: kompakt
    // modda gizlenince satır hesabına geri eklenir.
    var barPx by remember { mutableIntStateOf(0) }
    val inputFocus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Her oturum kendi kaydırma konumunu korur: sekmeler arasında geri
    // dönülünce aynı yerde açılır; ilk kez açılan oturum çıktının sonundan.
    val listStates = remember { HashMap<String, LazyListState>() }
    if (listStates.size > sessionList.size) listStates.keys.retainAll(sessionList.mapTo(HashSet()) { it.id })
    val listState = listStates.getOrPut(handle.id) { LazyListState(firstVisibleItemIndex = (lines.size - 1).coerceAtLeast(0)) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val t = Tok

    var pendingPaste by remember(handle.id) { mutableStateOf<String?>(null) }
    var menuOpen by remember(handle.id) { mutableStateOf(false) }
    var closing by remember(handle.id) { mutableStateOf(false) }
    var renaming by remember(handle.id) { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val hosts = repo?.items?.collectAsState()?.value.orEmpty()

    // OSC 52: uzak taraf panoya yazdı → cihaz panosuna aktar.
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(pendingClip) {
        pendingClip?.let {
            clipboard.setText(AnnotatedString(it))
            vm.consumeClipboard()
        }
    }

    val view = LocalView.current

    // BEL (\u0007): uzak zil → hafif haptic. Yalnız sayaç artınca; oturum
    // değişince başlangıç değeri yeniden okunur (geçişte titremez).
    LaunchedEffect(handle.id) {
        var last = vm.bellCount.value
        vm.bellCount.collect { n ->
            if (n > last) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            last = n
        }
    }

    // Ekranı açık tut (Ayarlar / taşma menüsü): yalnız çalışma alanı
    // görünürken; ekrandan çıkınca sistem zaman aşımı geri gelir.
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Tam ekran: sistem çubuklarını gizle.
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
    var ptySize by remember(handle.id) { mutableStateOf(vm.size) }
    val atBottom by remember(listState) {
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
    LaunchedEffect(viewportEpoch) {
        if (lines.isNotEmpty()) revealCursor()
    }

    // Alt-screen açıldığında (vim/htop/less/tmux) bekleyen gerçek boyutu
    // uzak tarafa ilet.
    val altActive by vm.altScreen.collectAsState()
    LaunchedEffect(altActive) {
        if (altActive) controller.send(TerminalInput.Resize(vm.size))
    }
    // Bağlantı ACTIVE'a geçince gerçek boyutu bir kez gönder (dedupe no-op).
    LaunchedEffect(state) {
        if (state == ConnectionState.ACTIVE) controller.send(TerminalInput.Resize(vm.size))
    }

    val matches = remember(lines, query) {
        if (query.length < 2) emptyList()
        else lines.mapIndexedNotNull { i, l -> if (l.text.contains(query, ignoreCase = true)) i else null }
    }
    val matchSet = remember(matches) { matches.toSet() }
    var matchCursor by remember(handle.id) { mutableIntStateOf(0) }
    val currentMatch = matches.getOrNull(matchCursor)

    // tmux geçmişinde gezinme: oturum tmux'ta olduğu için uygulama ekranı
    // alternatif ekrandadır ve geçmiş telefonda değil tmux'tadır. Fare
    // raporlaması açıkken (oturumlarımız `set mouse on` ile açılır) parmak
    // kaydırması tekerlek olayı olur; tmux copy-mode'a girip geçmişi kaydırır,
    // en alta inince kendiliğinden çıkar. Yukarıdayken bir şey yazılırsa önce
    // copy-mode kapatılır (yoksa tuşlar copy-mode komutu olurdu); ok ve sayfa
    // tuşları copy-mode içinde gezinmek için olduğu gibi gider.
    val mouseOn by vm.mouseTracking.collectAsState()
    var tmuxScrolled by remember(handle.id) { mutableStateOf(false) }
    val held = remember(handle.id) { ArrayDeque<String>() }
    var leaving by remember(handle.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun sendText(s: String) {
        if ((tmuxScrolled && s !in NAV_KEYS) || leaving != null) {
            held.addLast(s)
            if (leaving == null) {
                val tm = tmuxNames[handle.id]
                val ex = controller.exec()
                tmuxScrolled = false
                leaving = scope.launch {
                    if (tm != null && ex != null) {
                        runCatching { ex.exec("tmux send-keys -t '$tm' -X cancel 2>/dev/null || :", 3_000) }
                    }
                    while (held.isNotEmpty()) controller.send(TerminalInput.Text(held.removeFirst()))
                    leaving = null
                }
            }
            return
        }
        controller.send(TerminalInput.Text(s))
    }

    // Kaydırma bitince tmux'a sorulur: hâlâ copy-mode'da mı (en alta inince
    // tmux kendiliğinden çıkar)? "Canlıya dön" düğmesi ve yazmadan önceki
    // çıkış buna göre davranır.
    fun refreshTmuxMode() {
        val tm = tmuxNames[handle.id] ?: return
        val ex = controller.exec() ?: return
        scope.launch {
            val out = runCatching { ex.exec("tmux display -p -t '$tm' '#{pane_in_mode}' 2>/dev/null", 3_000).second.trim() }.getOrNull()
            if (out == "0") tmuxScrolled = false else if (out == "1") tmuxScrolled = true
        }
    }
    fun backToLive() {
        val tm = tmuxNames[handle.id] ?: return
        val ex = controller.exec() ?: return
        tmuxScrolled = false
        scope.launch { runCatching { ex.exec("tmux send-keys -t '$tm' -X cancel 2>/dev/null || :", 3_000) } }
    }

    // Yeni oturum: tek host varsa doğrudan açılır, birden fazlaysa seçici,
    // hiç yoksa Oturumlar'a dönülür. Secret yoksa düzenleyici sorar.
    fun openOn(c: SavedConnection) {
        val secret = repo?.secret(c.id)
        if (secret == null) onEditHost(c) else manager.open(c, secret, forceNew = true)
    }
    fun newSession() {
        when {
            repo == null || hosts.isEmpty() -> onNewConnection()
            hosts.size == 1 -> openOn(hosts.single())
            else -> picking = true
        }
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
        when {
            !s.contains('\n') -> sendText(s)
            vm.bracketedPaste -> sendText("\u001B[200~$s\u001B[201~")
            // Kabuk yapıştırmayı ayırt etmiyor: her satır Enter'la yazılmış
            // gibi çalışır. Göndermeden önce sorulur.
            else -> pendingPaste = s
        }
    }

    fun onImeChange(next: String) {
        when (val e = imeEdit(imeBuf, next)) {
            is ImeEdit.Insert -> emit(e.text)
            is ImeEdit.Delete -> repeat(e.count) { sendText("\u007F") }
            ImeEdit.None -> {}
        }
        imeBuf = if (next.length > IME_MAX) "" else next
    }

    // ── Agent eki: telefondan dosya/resim seç → SFTP ile
    // ~/.pocket-agent/uploads altına yükle → uzak yolu PTY'ye yaz.
    var uploading by remember { mutableStateOf(false) }
    var attachStatus by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // (metin, hata mı)
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (rawName, bytes) = pickedFile(context, uri) ?: return@rememberLauncherForActivityResult
        if (bytes.size > 10 * 1024 * 1024) {
            attachStatus = "10 MB sınırı aşıldı" to true
            return@rememberLauncherForActivityResult
        }
        val name = sanitizeRemoteName(rawName)
        uploading = true
        attachStatus = "Yükleniyor: $name" to false
        scope.launch {
            try {
                val path = controller.uploadForSession(name, bytes)
                emit(path)
                attachStatus = "Yüklendi → $path" to false
            } catch (e: Exception) {
                attachStatus = "Yüklenemedi: ${e.message ?: "hata"}" to true
            } finally {
                uploading = false
            }
        }
    }
    LaunchedEffect(attachStatus) {
        if (attachStatus != null) {
            kotlinx.coroutines.delay(4000)
            attachStatus = null
        }
    }

    // ── Web önizleme: host loopback'indeki yayın → SSH-içi forward → WebView.
    var previewPicker by remember { mutableStateOf(false) }
    var previewTarget by remember { mutableStateOf<PreviewTarget?>(null) }
    var previewToken by remember { mutableStateOf<String?>(null) }

    val console = LocalConsoleTheme.current
    val termBg = Color(console.term.background)
    val termFg = Color(console.term.foreground)
    val density = LocalDensity.current
    // Hücre genişliği gerçek fontla ölçülür — kestirim seçili mono fonta göre
    // saptığında remote cols yanlış açılır ve TUI agent'lar ekrana sığmaz.
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
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    // Kısa pencerede (yatay telefon) klavye açıkken oturum çubuğu gizlenir —
    // aksi halde terminale birkaç satır kalır. Klavye kapanınca geri gelir.
    // Klavye, gerçek inset yüksekliğiyle anlaşılır (gezinme çubuğunun ötesinde
    // ≥80dp); türetilmiş durum, animasyon karelerinde yeniden çizim yapmaz.
    val shortScreen = LocalConfiguration.current.screenHeightDp < 480
    val keyboardUp by remember(density, shortScreen) {
        derivedStateOf {
            shortScreen && imeInsets.getBottom(density) - navInsets.getBottom(density) > with(density) { 80.dp.roundToPx() }
        }
    }
    val compact = !fullscreen && keyboardUp
    val showBar = !fullscreen && !compact

    // Çubuktan aşağı çekme: çalışma alanı parmağı spring ile izler; eşik
    // altında bırakılırsa geri yaylanır, üstünde aşağı akıp Oturumlar'a döner.
    val reduced = rememberReducedMotion()
    var pull by remember { mutableFloatStateOf(0f) }
    val pullY by animateFloatAsState(
        pull,
        if (reduced) spring(stiffness = Spring.StiffnessHigh)
        else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pull",
    )
    val pullMax = with(density) { 320.dp.toPx() }
    val pullThreshold = with(density) { 72.dp.toPx() }
    val name = sessionLabel(handle, sessionList, customNames)
    val padH = 10.dp
    val padV = 6.dp

    // Boyut: her ölçümde, oturum değişince ve font değişince yeniden hesaplanır.
    // Klavye animasyonu her karede ölçüm üretir; efekt yeniden başladıkça
    // önceki bekleme iptal olur → PTY'ye yalnız son boyut gider (SIGWINCH
    // yağmuru yok). Satırlar klavyesiz ölçülür: klavye açılınca görünen alan
    // kısalır ama terminal modeli (ve remote PTY) değişmez. Gezinme çubuğu
    // payı iki durumda da vardır; yalnız klavyenin onun ötesine taşan kısmı
    // ve kompakt modda gizlenen çubuk geri eklenir.
    LaunchedEffect(handle.id, canvas, charW, lineH, compact) {
        if (canvas == IntSize.Zero) return@LaunchedEffect
        val padPx = with(density) { (padH * 2).toPx() }
        val padVPx = with(density) { (padV * 2).toPx() }
        val cols = ((canvas.width - padPx) / charW).toInt().coerceIn(20, 500)
        val imeExtra = (imeInsets.getBottom(density) - navInsets.getBottom(density)).coerceAtLeast(0)
        val hiddenBar = if (compact) barPx else 0
        // Yuvarla (floor değil): piksel-sapmalı ölçümler sınırda ±1 satır
        // üretmesin — gereksiz reflow'u önler.
        val rows = ((canvas.height - padVPx + imeExtra - hiddenBar) / lineH).roundToInt().coerceIn(4, 200)
        visRows = ((canvas.height - padVPx) / lineH).toInt().coerceAtLeast(1)
        kotlinx.coroutines.delay(160)
        val newSize = TerminalSize(cols, rows)
        if (newSize != vm.size) {
            vm.setSize(newSize)
            if (controller.state.value == ConnectionState.ACTIVE) controller.send(TerminalInput.Resize(newSize))
        }
        ptySize = newSize
    }

    // Tekerlek olayı: SGR (1006) ya da eski X10 kodlaması, parmağın altındaki
    // hücre konumuyla. up = eskiye (aşağı çekme).
    fun wheel(up: Boolean, at: androidx.compose.ui.geometry.Offset) {
        val padPx = with(density) { padH.toPx() }
        val padVPx = with(density) { padV.toPx() }
        val col = ((at.x - padPx) / charW).toInt().coerceIn(0, ptySize.cols - 1) + 1
        val row = ((at.y - padVPx) / lineH).toInt().coerceIn(0, ptySize.rows - 1) + 1
        val b = if (up) 64 else 65
        val seq = if (vm.sgrMouse) "\u001B[<$b;$col;${row}M"
        else "\u001B[M" + (32 + b).toChar() + (32 + col).coerceAtMost(255).toChar() + (32 + row).coerceAtMost(255).toChar()
        controller.send(TerminalInput.Text(seq))
        if (up && tmuxNames[handle.id] != null) tmuxScrolled = true
    }

    // Pinch-zoom geri bildirimi: ölçek değişince kısa süre ortada görünür.
    var zoomHud by remember { mutableStateOf(false) }
    LaunchedEffect(zoomHud, settings.theme.fontScale) {
        if (zoomHud) {
            kotlinx.coroutines.delay(900)
            zoomHud = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        // Çekme ipucu: eşiğe yaklaştıkça belirir.
        if (showBack && pullY > 1f) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = Space.lg)
                    .graphicsLayer { alpha = (pullY / pullThreshold).coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = t.text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (pullY >= pullThreshold) "Bırak, oturumlara dön" else "Oturumlara dönmek için çek",
                    style = MaterialTheme.typography.labelLarge,
                    color = t.text2,
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, pullY.roundToInt()) }
                .graphicsLayer { alpha = 1f - (pullY / pullMax) * 0.35f }
                .clip(if (pullY > 1f) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(0.dp))
                .background(termBg)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.ime).union(WindowInsets.navigationBars)),
        ) {
            if (showBar) {
                // ── Oturum çubuğu: aşağı çekilebilir. Düğme dokunuşları çalışır;
                // sürükleme yalnız dikey kayma eşiği aşılınca olayı alır.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { barPx = it.height }
                        .then(
                            if (showBack) Modifier.pointerInput(Unit) {
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
                            } else Modifier,
                        ),
                ) {
                    if (showBack) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .align(Alignment.CenterHorizontally)
                                .size(width = 32.dp, height = 4.dp)
                                .clip(PillShape)
                                .background(t.lineStrong),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showBack) {
                            IconAction(Icons.Rounded.ExpandMore, "Oturumlara dön", onCollapse, tint = t.text)
                        } else {
                            Spacer(Modifier.width(Space.md))
                        }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(name, style = MaterialTheme.typography.titleMedium, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val st = if (retry > 0) Status.Warn else state.status()
                                StatusDot(st, size = 7.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    listOfNotNull(
                                        state.label(retry),
                                        (conn ?: handle.conn).let { "${it.user}@${it.host}" },
                                        if (connected) "${ptySize.cols}×${ptySize.rows}" else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (st == Status.Error) t.danger.text else if (st == Status.Warn) t.warn.text else t.text2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconAction(Icons.Rounded.Search, "Scrollback'te ara", { chrome.searchOpen = !chrome.searchOpen }, selected = searchOpen)
                        IconAction(Icons.Rounded.Folder, "Dosyalar", onOpenFiles, enabled = connected && controller.sftp() != null)
                        Box {
                            IconAction(Icons.Rounded.MoreVert, "Diğer", { menuOpen = true })
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = t.raised) {
                                MenuItem(Icons.Rounded.Add, "Yeni oturum") {
                                    menuOpen = false
                                    newSession()
                                }
                                MenuItem(Icons.Rounded.Edit, "Yeniden adlandır") { menuOpen = false; renaming = true }
                                MenuItem(Icons.Rounded.Language, "Web önizleme", enabled = connected && controller.tcpip() != null) {
                                    menuOpen = false; previewPicker = true
                                }
                                MenuItem(Icons.Rounded.Share, "Scrollback'i paylaş") { menuOpen = false; shareScrollback(context, lines) }
                                MenuItem(Icons.Rounded.Fullscreen, "Tam ekran") { menuOpen = false; chrome.fullscreen = true }
                                MenuItem(
                                    Icons.Rounded.LightMode, "Ekranı açık tut",
                                    checked = settings.keepScreenOn,
                                ) { menuOpen = false; settings.toggleKeepScreenOn() }
                                if ((state == ConnectionState.CLOSED || state == ConnectionState.FAILED) && controller.canReconnect()) {
                                    MenuItem(Icons.Rounded.Refresh, "Yeniden bağlan") { menuOpen = false; controller.reconnect() }
                                }
                                HorizontalDivider(color = t.line)
                                MenuItem(Icons.Rounded.Close, "Oturumu kapat…", danger = true) { menuOpen = false; closing = true }
                            }
                        }
                    }
                    if (sessionList.size > 1) {
                        SessionTabs(
                            sessions = sessionList,
                            activeId = handle.id,
                            names = customNames,
                            onSelect = { manager.setActive(it) },
                            onAdd = { newSession() },
                        )
                    }
                }
            }

            // ── Terminal yüzeyi: dokun → doğrudan yaz (ayrı giriş satırı yok) ──
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(termBg)
                    .semantics { contentDescription = "Terminal çıktısı" }
                    .onSizeChanged {
                        canvas = it
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
                    // Pinch-to-zoom: iki parmak fontScale'i anlık büyütür/küçültür.
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) {
                                settings.setFontScale(settings.theme.fontScale * zoom)
                                zoomHud = true
                            }
                        }
                    }
                    // Fare raporlaması açıkken tek parmak dikey kaydırma →
                    // tekerlek olayları. tmux her olayda 5 satır kaydırır; adım
                    // da 5 satır olduğundan içerik parmağı izler. Hızlı fırlatma
                    // kısa süre devam eder; yeni dokunuş durdurur. Aşağı çekmek
                    // eskiye gider. İki parmak (zoom) ve seçim jesti karışmaz.
                    .then(
                        if (mouseOn && connected) Modifier.pointerInput(lineH, charW) {
                            val step = lineH * 5
                            fun emit(acc0: Float, at: androidx.compose.ui.geometry.Offset): Float {
                                var acc = acc0
                                while (acc >= step) { wheel(up = true, at = at); acc -= step }
                                while (acc <= -step) { wheel(up = false, at = at); acc += step }
                                return acc
                            }
                            kotlinx.coroutines.coroutineScope {
                                var fling: kotlinx.coroutines.Job? = null
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    fling?.cancel()
                                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                                    tracker.addPosition(down.uptimeMillis, down.position)
                                    var acc = 0f
                                    var dragging = false
                                    var at = down.position
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.count { it.pressed } > 1) { dragging = false; break }
                                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (c.isConsumed) { dragging = false; break }
                                        tracker.addPosition(c.uptimeMillis, c.position)
                                        at = c.position
                                        if (!c.pressed) break
                                        acc += c.position.y - c.previousPosition.y
                                        if (!dragging) {
                                            if (kotlin.math.abs(acc) < viewConfiguration.touchSlop) continue
                                            dragging = true
                                            acc = 0f
                                        }
                                        c.consume()
                                        acc = emit(acc, at)
                                    }
                                    val v0 = if (dragging) tracker.calculateVelocity().y else 0f
                                    if (kotlin.math.abs(v0) > 800f) {
                                        fling = launch {
                                            var v = v0
                                            var a = 0f
                                            while (kotlin.math.abs(v) > 250f) {
                                                a = emit(a + v * 0.016f, at)
                                                v *= 0.95f
                                                kotlinx.coroutines.delay(16)
                                            }
                                            refreshTmuxMode()
                                        }
                                    } else if (dragging) {
                                        refreshTmuxMode()
                                    }
                                }
                            }
                        } else Modifier,
                    ),
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
                                // Alt+harf → ESC öneki (kabukta kelime atlama/silme).
                                ev.isAltPressed && k.keyCode in Key.A.keyCode..Key.Z.keyCode -> {
                                    val letter = 'a'.code + (k.keyCode - Key.A.keyCode).toInt()
                                    sendText("\u001B" + letter.toChar()); true
                                }
                                k == Key.Enter || k == Key.NumPadEnter -> { sendText("\r"); true }
                                k == Key.Backspace -> { sendText("\u007F"); true }
                                k == Key.Tab -> { sendText(if (ev.isShiftPressed) "\u001B[Z" else "\t"); true }
                                k == Key.Delete -> { sendText("\u001B[3~"); true }
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
                    // Boş çıktı: durum satırı + yanıp sönen imleç bloğu.
                    val blink = rememberBlink(state == ConnectionState.CONNECTING && !reduced)
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (state) {
                                    ConnectionState.CONNECTING -> "❯ bağlanıyor…"
                                    ConnectionState.ACTIVE -> "❯ yazmak için dokun"
                                    ConnectionState.FAILED -> "❯ bağlantı kurulamadı"
                                    else -> "❯ bekleniyor"
                                },
                                color = when (state) {
                                    ConnectionState.CONNECTING -> t.busy.text
                                    ConnectionState.FAILED -> t.danger.text
                                    else -> t.text2
                                },
                                fontFamily = LocalMonoFont.current,
                                fontSize = (14 * settings.theme.fontScale).sp,
                            )
                            Spacer(Modifier.width(3.dp))
                            Box(
                                Modifier
                                    .width(8.dp)
                                    .height(16.dp)
                                    .graphicsLayer { alpha = if (blink.value) 1f else 0.15f }
                                    .background(Color(console.term.cursor)),
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = padH, vertical = padV),
                            // Fare raporlaması açıkken kaydırma uzak tarafa gider.
                            userScrollEnabled = !mouseOn,
                        ) {
                            itemsIndexed(lines, contentType = { _, _ -> 0 }) { idx, line ->
                                val isMatch = currentMatch == idx
                                val hasMatch = matchSet.contains(idx)
                                val cur = cursor
                                val cursorCol = if (cur != null && cur.first == idx) cur.second else -1
                                val annotated = remember(line, cursorCol, console.term.ansi, console.term.cursor) {
                                    line.toAnnotatedString(cursorCol, Color(console.term.cursor), console.term.ansi)
                                }
                                Text(
                                    annotated,
                                    color = termFg,
                                    fontFamily = LocalMonoFont.current,
                                    fontSize = (13 * settings.theme.fontScale).sp,
                                    lineHeight = (16 * settings.theme.fontScale).sp,
                                    modifier = Modifier.fillMaxWidth().background(
                                        when {
                                            isMatch -> t.primary.copy(alpha = 0.34f)
                                            hasMatch -> t.primary.copy(alpha = 0.12f)
                                            else -> Color.Transparent
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }

                // Pinch-zoom okuması: yeni ölçek ve terminal boyutu.
                if (zoomHud) {
                    Text(
                        "%.1f×".format(settings.theme.fontScale) + " · ${ptySize.cols}×${ptySize.rows}",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = Code),
                        color = t.text,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(PillShape)
                            .background(t.raised.copy(alpha = 0.94f))
                            .padding(horizontal = Space.lg, vertical = Space.sm),
                    )
                }

                // Alta in (çıktı alanı içinde; tuş panelinin üstünde).
                if (!atBottom && lines.size > 1) {
                    Surface(
                        color = t.raised,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, t.line),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) {
                        IconAction(
                            Icons.Rounded.KeyboardArrowDown, "En alta in",
                            {
                                scope.launch { listState.scrollToItem((lines.size - visRows).coerceAtLeast(0)) }
                            },
                            tint = t.text,
                        )
                    }
                }

                // tmux geçmişinde yukarıdayken: tek dokunuşla canlı görünüme dön.
                if (tmuxScrolled && mouseOn) {
                    Surface(
                        color = t.raised,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, t.line),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) {
                        IconAction(Icons.Rounded.KeyboardArrowDown, "Canlı görünüme dön", { backToLive() }, tint = t.text)
                    }
                }

                // Ek yükleme durumu: geçici şerit (4 sn sonra silinir).
                attachStatus?.let { (msg, isError) ->
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isError) t.danger.soft else t.raised)
                            .padding(horizontal = Space.md, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = t.primary)
                        } else {
                            StatusDot(if (isError) Status.Error else Status.Live)
                        }
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            msg,
                            color = if (isError) t.danger.onSoft else t.text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Hata bandı: neden + yapılabilir eylem.
                if (state == ConnectionState.FAILED && failure != null) {
                    Banner(
                        Status.Error,
                        failureTitle(failure!!),
                        body = failureText(failure!!),
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                        actions = {
                            GhostButton("Hostu düzenle", onClick = { onEditHost(handle.conn) }, color = t.text2)
                            if (controller.canReconnect()) {
                                GhostButton("Yeniden dene", onClick = { controller.reconnect() })
                            }
                        },
                    )
                }

                // Arama çubuğu
                if (searchOpen) {
                    SearchBar(
                        query = query,
                        onQuery = { query = it; matchCursor = 0 },
                        count = matches.size,
                        index = matchCursor,
                        onPrev = {
                            matchCursor = (matchCursor - 1 + matches.size) % matches.size
                            scope.launch { listState.scrollToItem(matches[matchCursor]) }
                        },
                        onNext = {
                            matchCursor = (matchCursor + 1) % matches.size
                            scope.launch { listState.scrollToItem(matches[matchCursor]) }
                        },
                        onClose = { chrome.searchOpen = false },
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    )
                }

                // Tam ekranda çubuk gizli → küçük eylem kapsülü sağ üstte.
                if (fullscreen && !searchOpen) {
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(PillShape)
                            .background(t.raised.copy(alpha = 0.92f))
                            .padding(horizontal = 4.dp),
                    ) {
                        IconAction(Icons.Rounded.Search, "Scrollback'te ara", { chrome.searchOpen = true })
                        IconAction(Icons.Rounded.Share, "Scrollback'i paylaş", { shareScrollback(context, lines) })
                        IconAction(Icons.Rounded.FullscreenExit, "Tam ekrandan çık", { chrome.fullscreen = false }, tint = t.text)
                    }
                }
            }

            // ── Yüzen tuş paneli (tam ekranda da) ──
            KeyToolbar(
                ctrl = ctrl,
                enabled = connected,
                typing = typing,
                haptics = settings.keyHaptics,
                snippets = settings.snippetList(),
                attachEnabled = connected && !uploading && controller.sftp() != null,
                onAttach = { attachLauncher.launch("*/*") },
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

    if (previewPicker) {
        PreviewPortSheet(
            detected = previewTargetsFromTexts(lines.takeLast(400).map { it.text }),
            scrollToken = previewTokenFromTexts(lines.takeLast(400).map { it.text }),
            probe = {
                val e = controller.exec() ?: return@PreviewPortSheet PreviewProbe(emptyList(), null)
                try {
                    parsePreviewProbe(e.exec(PREVIEW_PROBE_CMD).second)
                } catch (_: Exception) {
                    PreviewProbe(emptyList(), null)
                }
            },
            onDismiss = { previewPicker = false },
            onOpen = { target, tok ->
                previewPicker = false
                previewTarget = target
                previewToken = tok
            },
        )
    }
    previewTarget?.let { target ->
        // Sağlayıcı her accept'te GÜNCEL transport'u çözer — SSH kopup retry
        // ile yeni transport gelince forwarder kapanmadan toparlanır.
        PreviewScreen({ controller.tcpip() }, target, previewToken) { previewTarget = null }
    }
    if (closing) {
        CloseSessionSheet(handle, manager, name = name, tmux = tmuxNames[handle.id]) { closed ->
            closing = false
            if (closed && manager.sessions.value.isEmpty()) onCollapse()
        }
    }
    if (renaming) {
        RenameSessionDialog(
            connName = handle.conn.name,
            initial = customNames[handle.id],
            onDismiss = { renaming = false },
            onSave = { n -> manager.rename(handle.id, n); renaming = false },
        )
    }
    pendingPaste?.let { text ->
        val lines = text.trimEnd('\n').lines()
        ConfirmDialog(
            title = "${lines.size} satır yapıştırılsın mı?",
            message = "Bu kabuk yapıştırılan metni ayırt etmiyor; her satır Enter'a basılmış gibi komut olarak çalışabilir.\n\n" +
                lines.take(3).joinToString("\n") { it.take(80) } + if (lines.size > 3) "\n…" else "",
            confirmLabel = "Yapıştır",
            onConfirm = { sendText(text); pendingPaste = null },
            onDismiss = { pendingPaste = null },
        )
    }

    if (picking) {
        HostPickerSheet(
            title = "Yeni oturum",
            subtitle = "Aynı host'ta paralel terminal açabilirsin.",
            hosts = hosts,
            onDismiss = { picking = false },
            onPick = { c -> picking = false; openOn(c) },
        )
    }
}

// Yalnız etkinken tıklayan imleç saati; kapalıyken sabit "açık".
@Composable
private fun rememberBlink(enabled: Boolean): androidx.compose.runtime.State<Boolean> {
    val on = remember { mutableStateOf(true) }
    LaunchedEffect(enabled) {
        on.value = true
        while (enabled) {
            kotlinx.coroutines.delay(530)
            on.value = !on.value
        }
    }
    return on
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    val t = Tok
    DropdownMenuItem(
        text = { Text(label, color = if (!enabled) t.text3 else if (danger) t.danger.text else t.text) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = if (!enabled) t.text3 else if (danger) t.danger.fg else t.text2) },
        trailingIcon = checked?.let { on ->
            { PocketSwitch(checked = on, onCheckedChange = null, modifier = Modifier.scale(0.8f)) }
        },
        enabled = enabled,
        onClick = onClick,
        modifier = if (checked != null) Modifier.semantics { toggleableState = androidx.compose.ui.state.ToggleableState(checked) } else Modifier,
    )
}

// Oturum sekmeleri (iki ve daha fazla oturumda): durum noktası + ad +
// yeniden deneme sayacı. Uzun ad kırpılır, şerit yatay kayar.
@Composable
private fun SessionTabs(
    sessions: List<SessionHandle>,
    activeId: String,
    names: Map<String, String>,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val t = Tok
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = Space.md, end = Space.sm, bottom = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sessions.forEach { h ->
            val st by h.controller.state.collectAsState()
            val retry by h.controller.retryAttempt.collectAsState()
            val selected = h.id == activeId
            Row(
                Modifier
                    .clip(PillShape)
                    .background(if (selected) t.card else Color.Transparent)
                    .then(if (selected) Modifier else Modifier.border(1.dp, t.line, PillShape))
                    .clickable(onClick = { onSelect(h.id) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(if (retry > 0) Status.Warn else st.status(), size = 7.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    sessionLabel(h, sessions, names),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (selected) t.text else t.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
                if (retry > 0) {
                    Text(
                        " ↻$retry/${TerminalController.MAX_RETRY}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = Code, fontWeight = FontWeight.Normal),
                        color = t.warn.text,
                        maxLines = 1,
                    )
                }
            }
        }
        IconAction(Icons.Rounded.Add, "Yeni oturum", onAdd)
    }
}

@Composable
internal fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    index: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Scrollback'te ara",
) {
    val t = Tok
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(t.raised)
            .border(1.dp, t.line, RoundedCornerShape(18.dp))
            .padding(start = Space.lg, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = t.text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = t.text3)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Code, color = t.text),
                cursorBrush = SolidColor(t.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { if (count > 0) onNext() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Text(
            if (count == 0) "0" else "${index + 1}/$count",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Code, fontWeight = FontWeight.Normal),
            color = t.text2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconAction(Icons.Rounded.KeyboardArrowUp, "Önceki eşleşme", onPrev, enabled = count > 0)
        IconAction(Icons.Rounded.KeyboardArrowDown, "Sonraki eşleşme", onNext, enabled = count > 0)
        IconAction(Icons.Rounded.Close, "Aramayı kapat", onClose)
    }
}

// ── Tuş paneli ──────────────────────────────────────────────────────────────
// Solda ctrl mandalı, ortada kayan tuşlar (agent istemleri için en sık
// gerekenler başta: esc, enter, oklar), sağda yapıştır / dosya ekle /
// klavye. Ripple yok; basılı zemin + (açıksa) haptic. Oklar ve sayfa
// tuşları basılı tutulunca tekrar eder.

@Composable
private fun KeyToolbar(
    ctrl: Boolean,
    enabled: Boolean,
    typing: Boolean,
    haptics: Boolean,
    snippets: List<Pair<String, String>>,
    attachEnabled: Boolean,
    onAttach: () -> Unit,
    onCtrl: () -> Unit,
    onKey: (String) -> Unit,
    onPaste: () -> Unit,
    onKeyboard: () -> Unit,
) {
    val t = Tok
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.sm, vertical = Space.sm)
            .clip(RoundedCornerShape(20.dp))
            .background(t.raised)
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyCap("ctrl", enabled, haptics, latched = ctrl, desc = if (ctrl) "Ctrl basılı" else null, onTap = onCtrl)
        KeySep()
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            KeyCap("esc", enabled, haptics) { onKey("\u001B") }
            IconKeyCap(Icons.AutoMirrored.Rounded.KeyboardReturn, "Enter", enabled, haptics) { onKey("\r") }
            IconKeyCap(Icons.Rounded.KeyboardArrowUp, "Yukarı ok", enabled, haptics, repeat = true) { onKey("\u001B[A") }
            IconKeyCap(Icons.Rounded.KeyboardArrowDown, "Aşağı ok", enabled, haptics, repeat = true) { onKey("\u001B[B") }
            IconKeyCap(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Sol ok", enabled, haptics, repeat = true) { onKey("\u001B[D") }
            IconKeyCap(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Sağ ok", enabled, haptics, repeat = true) { onKey("\u001B[C") }
            KeyCap("tab", enabled, haptics) { onKey("\t") }
            KeyCap("⇧tab", enabled, haptics, desc = "Shift Tab") { onKey("\u001B[Z") }
            KeySep()
            KeyCap("^C", enabled, haptics, desc = "Ctrl C") { onKey("\u0003") }
            KeyCap("^D", enabled, haptics, desc = "Ctrl D") { onKey("\u0004") }
            KeyCap("^Z", enabled, haptics, desc = "Ctrl Z") { onKey("\u001A") }
            KeyCap("^L", enabled, haptics, desc = "Ctrl L") { onKey("\u000C") }
            KeySep()
            KeyCap("home", enabled, haptics) { onKey("\u001B[H") }
            KeyCap("end", enabled, haptics) { onKey("\u001B[F") }
            KeyCap("pgup", enabled, haptics, repeat = true) { onKey("\u001B[5~") }
            KeyCap("pgdn", enabled, haptics, repeat = true) { onKey("\u001B[6~") }
            KeySep()
            KeyCap("|", enabled, haptics) { onKey("|") }
            KeyCap("~", enabled, haptics) { onKey("~") }
            KeyCap("-", enabled, haptics) { onKey("-") }
            KeyCap("_", enabled, haptics) { onKey("_") }
            KeyCap("/", enabled, haptics) { onKey("/") }
            // Kullanıcı snippet'ları (Ayarlar → Terminal): etiket basılır,
            // komut metni gönderilir (Enter kullanıcıda — iptal şansı kalır).
            if (snippets.isNotEmpty()) KeySep()
            snippets.forEach { (label, cmd) ->
                KeyCap(label, enabled, haptics, accent = true) { onKey(cmd) }
            }
        }
        KeySep()
        IconKeyCap(Icons.Rounded.ContentPaste, "Yapıştır", enabled, haptics, onTap = onPaste)
        IconKeyCap(Icons.Rounded.AttachFile, "Dosya ekle", attachEnabled, haptics, onTap = onAttach)
        IconKeyCap(
            if (typing) Icons.Rounded.KeyboardHide else Icons.Rounded.Keyboard,
            if (typing) "Klavyeyi kapat" else "Klavyeyi aç",
            enabled,
            haptics,
            onTap = onKeyboard,
        )
    }
}

// Tuş dokunuşu: parmak kalkınca bir kez basar; şerit kaydırılırsa tuş
// tetiklenmez (kaydırma olayı tüketince iptal). repeat açıkken 400ms
// basılı tutmak ~16/sn tekrar başlatır. Ekran okuyucu için düğme rolü ve
// tıklama eylemi ayrıca tanımlanır.
@Composable
private fun Modifier.keyPress(
    enabled: Boolean,
    haptics: Boolean,
    repeat: Boolean,
    desc: String?,
    onPressed: (Boolean) -> Unit,
    onTap: () -> Unit,
): Modifier {
    val view = LocalView.current
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val buzz by androidx.compose.runtime.rememberUpdatedState(haptics)
    fun fire() {
        if (buzz) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        tap()
    }
    return this
        .semantics(mergeDescendants = true) {
            role = androidx.compose.ui.semantics.Role.Button
            if (desc != null) contentDescription = desc
            if (!enabled) disabled()
            onClick { if (enabled) { tap(); true } else false }
        }
        .pointerInput(enabled, repeat) {
            if (!enabled) return@pointerInput
            kotlinx.coroutines.coroutineScope {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressed(true)
                    var repeated = false
                    val job = if (repeat) {
                        launch {
                            kotlinx.coroutines.delay(400)
                            fire()
                            repeated = true
                            while (true) {
                                kotlinx.coroutines.delay(60)
                                tap()
                            }
                        }
                    } else null
                    val up = waitForUpOrCancellation()
                    job?.cancel()
                    onPressed(false)
                    if (up != null && !repeated) fire()
                }
            }
        }
}

@Composable
private fun KeyCap(
    label: String,
    enabled: Boolean,
    haptics: Boolean,
    latched: Boolean = false,
    accent: Boolean = false,
    repeat: Boolean = false,
    desc: String? = null,
    onTap: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val t = Tok
    Box(
        Modifier
            .height(40.dp)
            .defaultMinSize(minWidth = 40.dp)
            .clip(KeyShape)
            .background(
                when {
                    latched -> t.primary
                    pressed && enabled -> t.overlay
                    accent -> t.accent.soft
                    else -> t.card
                },
            )
            .keyPress(enabled, haptics, repeat, desc, { pressed = it }, onTap)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = Code,
                fontWeight = if (latched) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
            color = when {
                !enabled -> t.text3
                latched -> t.onPrimary
                accent -> t.accent.onSoft
                else -> t.text
            },
        )
    }
}

@Composable
private fun IconKeyCap(
    icon: ImageVector,
    desc: String,
    enabled: Boolean,
    haptics: Boolean,
    repeat: Boolean = false,
    onTap: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val t = Tok
    Box(
        Modifier
            .size(40.dp)
            .clip(KeyShape)
            .background(if (pressed && enabled) t.overlay else t.card)
            .keyPress(enabled, haptics, repeat, desc, { pressed = it }, onTap),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (enabled) t.text else t.text3)
    }
}

// Tuş grupları arası ince çizgi: kontrol / ok / gezinme / sembol / snippet.
@Composable
private fun KeySep() {
    Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(22.dp).background(Tok.line))
}

// copy-mode içinde gezinmeye yarayan tuşlar (oklar, sayfa, home/end).
private val NAV_KEYS = setOf(
    "\u001B[A", "\u001B[B", "\u001B[C", "\u001B[D",
    "\u001B[5~", "\u001B[6~", "\u001B[H", "\u001B[F",
)

private fun failureTitle(f: TransportFailure): String = when (f) {
    is TransportFailure.AuthFailed -> "Kimlik doğrulama reddedildi"
    is TransportFailure.HostKeyChanged -> "Host anahtarı değişti"
    is TransportFailure.Network -> "Ağ hatası"
    is TransportFailure.MissingServer -> "Sunucu bileşeni eksik"
}

private fun failureText(f: TransportFailure): String = when (f) {
    is TransportFailure.AuthFailed -> "Parolayı ya da anahtarı kontrol et. Başka yönteme geçilmez."
    is TransportFailure.HostKeyChanged -> "Olası araya girme saldırısı; bağlantı durduruldu. Ayarlar → Bilinen host anahtarları'ndan unutup yeniden eşle."
    is TransportFailure.Network -> f.reason
    is TransportFailure.MissingServer -> f.what
}

fun defaultTerminalSize(): TerminalSize = TerminalSize(80, 24)

// ── Web önizleme ────────────────────────────────────────────────────────────
// Host loopback'indeki web yayınını (kimi web, dev server) SSH-içi forward
// üzerinden WebView'de açar. Port adayları: terminal çıktısındaki loopback
// URL'leri ("çıktıda" etiketi) + ss/netstat sondası + elle giriş.

@Composable
private fun PreviewPortSheet(
    detected: List<PreviewTarget>,
    scrollToken: String?,
    probe: suspend () -> PreviewProbe,
    onDismiss: () -> Unit,
    onOpen: (PreviewTarget, String?) -> Unit,
) {
    var manual by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf<List<Int>?>(null) }
    // Scrollback'teki `Token:` satırı öncelikli; yoksa sondanın okuduğu token.
    var probeToken by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val p = probe()
        listening = p.ports
        probeToken = p.token
    }
    val t = Tok
    PocketSheet(
        onDismiss = onDismiss,
        title = "Web önizleme",
        subtitle = "Host'ta dinleyen web arayüzünü SSH tüneli üzerinden aç.",
    ) {
        val detectedPorts = detected.map { it.port }.toSet()
        val targets = (detected + (listening ?: emptyList()).map { PreviewTarget("localhost", it) }).distinctBy { it.port }
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            when {
                listening == null && targets.isEmpty() -> Row(
                    Modifier.padding(horizontal = Space.xxl, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primary)
                    Spacer(Modifier.width(Space.md))
                    Text("Dinlenen portlar aranıyor…", style = MaterialTheme.typography.bodyMedium, color = t.text2)
                }
                targets.isEmpty() -> Text(
                    "Dinlenen port bulunamadı. Portu aşağıya elle gir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.text2,
                    modifier = Modifier.padding(horizontal = Space.xxl, vertical = Space.md),
                )
                else -> targets.forEach { target ->
                    ListRow(
                        title = "${target.host}:${target.port}",
                        titleMono = true,
                        contentPadding = PaddingValues(horizontal = Space.xxl, vertical = Space.md),
                        leading = { IconTile(Icons.Rounded.Language, tone = t.busy, size = 36.dp) },
                        trailing = { if (target.port in detectedPorts) Tag("çıktıda", tone = t.accent) },
                        onClick = { onOpen(target, scrollToken ?: probeToken) },
                    )
                }
            }
        }
        Row(
            Modifier.padding(horizontal = Space.xxl, vertical = Space.md),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            PocketTextField(
                manual,
                { manual = it.filter(Char::isDigit).take(5) },
                label = "Port",
                placeholder = "3000",
                mono = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            TonalButton(
                "Aç",
                enabled = manual.toIntOrNull() in 1..65535,
                onClick = { onOpen(PreviewTarget("localhost", manual.toInt()), scrollToken ?: probeToken) },
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

// Tam ekran önizleme: forwarder telefonda 127.0.0.1:<yerel> dinler, her
// bağlantı host'un loopback'ine SSH içinden bağlanır. WebView
// http://127.0.0.1:<yerel><path> yükler — 127.0.0.1 cleartext allowlist'te.
// kimi web'in bearer credential'ı varsa `#token=` fragment'ıyla otomatik
// oturum açar; kopyala düğmesi token-giriş sayfası için elle yedek.
@Composable
private fun PreviewScreen(
    tcpipProvider: () -> TcpipCapable?,
    target: PreviewTarget,
    token: String?,
    onClose: () -> Unit,
) {
    FullScreenDialog(onDismiss = onClose) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))) {
            val fwdScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
            // Kopan SSH yerine yenisini kullanan sarmalayıcı.
            val tcpip = remember {
                object : TcpipCapable {
                    override suspend fun openTcpip(host: String, port: Int) =
                        (tcpipProvider() ?: throw java.io.IOException("SSH bağlı değil")).openTcpip(host, port)
                }
            }
            val forwarder = remember(target) { LocalForwarder(fwdScope, tcpip, target.port, target.host) }
            var localPort by remember { mutableIntStateOf(0) }
            var error by remember { mutableStateOf<String?>(null) }
            var pageError by remember { mutableStateOf<String?>(null) }
            var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val clipboard = LocalClipboardManager.current
            LaunchedEffect(target) {
                try {
                    // Uzak portla aynı yerel port → Host/Origin birebir eşleşir;
                    // doluysa rastgele porta düşer.
                    localPort = forwarder.start(target.port)
                } catch (e: Exception) {
                    error = e.message ?: "tünel açılamadı"
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    forwarder.close()
                    fwdScope.cancel()
                    webView?.destroy()
                }
            }
            PocketTopBar(
                title = "${target.host}:${target.port}",
                onBack = onClose,
                backIcon = Icons.Rounded.Close,
                backDesc = "Önizlemeyi kapat",
                insetTop = false,
                subtitle = { Text("SSH tüneli · 127.0.0.1", style = MaterialTheme.typography.bodySmall) },
            ) {
                if (token != null) {
                    IconAction(Icons.Rounded.ContentCopy, "Token'ı kopyala", { clipboard.setText(AnnotatedString(token)) })
                }
                IconAction(Icons.Rounded.Refresh, "Yenile", { webView?.reload() }, enabled = localPort != 0)
                IconAction(
                    Icons.AutoMirrored.Rounded.OpenInNew, "Tarayıcıda aç",
                    {
                        if (localPort != 0) {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("http://127.0.0.1:$localPort${target.localPath(token)}"),
                                ),
                            )
                        }
                    },
                    enabled = localPort != 0,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    error != null -> Banner(
                        Status.Error, "Tünel açılamadı", body = error,
                        modifier = Modifier.align(Alignment.Center).padding(Space.xxl),
                    )
                    localPort == 0 -> Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Tok.primary)
                        Spacer(Modifier.width(Space.md))
                        Text("Tünel açılıyor…", style = MaterialTheme.typography.bodyMedium, color = Tok.text2)
                    }
                    else -> AndroidView(
                        factory = { c ->
                            android.webkit.WebView(c).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onReceivedError(
                                        v: android.webkit.WebView,
                                        req: android.webkit.WebResourceRequest,
                                        err: android.webkit.WebResourceError,
                                    ) {
                                        if (req.isForMainFrame) pageError = err.description.toString()
                                    }
                                    override fun onReceivedHttpError(
                                        v: android.webkit.WebView,
                                        req: android.webkit.WebResourceRequest,
                                        resp: android.webkit.WebResourceResponse,
                                    ) {
                                        if (req.isForMainFrame) pageError = "HTTP ${resp.statusCode}"
                                    }
                                    override fun onPageFinished(v: android.webkit.WebView, url: String) {
                                        pageError = null
                                    }
                                }
                                loadUrl("http://127.0.0.1:$localPort${target.localPath(token)}")
                                webView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Ana frame hatası: beyaz ekran yerine görünür mesaj + kanal teşhisi.
                pageError?.let { pe ->
                    Banner(
                        Status.Error,
                        "Sayfa yüklenemedi",
                        body = listOfNotNull(pe, forwarder.lastChannelError?.let { "tünel: $it" }).joinToString("\n"),
                        modifier = Modifier.align(Alignment.Center).padding(Space.xxl),
                        actions = { GhostButton("Tekrar dene", onClick = { pageError = null; webView?.reload() }) },
                    )
                }
            }
        }
    }
}
