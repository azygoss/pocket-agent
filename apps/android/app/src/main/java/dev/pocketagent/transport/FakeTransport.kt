// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// JVM testleri ve UI preview için sahte transport: PTY yankısı yapar,
// resize'ları kaydeder, kapatılınca read() sonlanır. Gerçek bağlantı
// SshjConnector üzerinden akar (ayni SshTransport arayüzü).
class FakeSshTransport : SshTransport {
    private var size = TerminalSize(80, 24)
    private var opened = false
    private val outbox = Channel<TerminalFrame>(Channel.UNLIMITED)
    val resizes = mutableListOf<TerminalSize>()
    val sent = mutableListOf<TerminalInput>()
    override val transport = TerminalTransport.SSH
    private var closed = false

    override suspend fun openPty(term: String, size: TerminalSize) {
        check(!closed)
        opened = true
        this.size = size
        outbox.trySend(TerminalFrame("[$term ${size.cols}x${size.rows}]\n".toByteArray(), transport))
    }

    override suspend fun send(input: TerminalInput) {
        check(opened && !closed)
        sent.add(input)
        when (input) {
            is TerminalInput.Text -> outbox.trySend(TerminalFrame(("> " + input.s).toByteArray(), transport))
            is TerminalInput.Key -> outbox.trySend(TerminalFrame(("[key:${input.code}]").toByteArray(), transport))
            is TerminalInput.Resize -> {
                size = input.size
                resizes.add(size)
                outbox.trySend(TerminalFrame(("[resize ${size.cols}x${size.rows}]").toByteArray(), transport))
            }
        }
    }

    override suspend fun read(): TerminalFrame {
        check(opened)
        return outbox.receive() // suspends until data; throws once closed
    }

    override fun poll(): TerminalFrame? = outbox.tryReceive().getOrNull()

    override suspend fun resize(size: TerminalSize) = send(TerminalInput.Resize(size))
    override fun close() { closed = true; outbox.close() }
}

// Ekran ViewModel'i: TerminalBuffer (satır-tabanlı, stilli) + giriş + boyut +
// transport rozeti + komut geçmişi. frames = düz metin görünüm (test uyumu).
class TerminalViewModel(val session: SessionId, private val maxLines: Int = 50_000) {
    private val buffer = TerminalBuffer(maxLines)
    private val _lines = MutableStateFlow<List<TermLine>>(emptyList())
    val lines: StateFlow<List<TermLine>> = _lines
    private val _cursor = MutableStateFlow<Pair<Int, Int>?>(null)
    val cursor: StateFlow<Pair<Int, Int>?> = _cursor
    // OSC 0/2 pencere başlığı (tmux/vim başlığı burada görünür)
    private val _windowTitle = MutableStateFlow("")
    val windowTitle: StateFlow<String> = _windowTitle
    // OSC 52: uzaktan kopyalama — UI sisteme panosuna yazar
    private val _pendingClipboard = MutableStateFlow<String?>(null)
    val pendingClipboard: StateFlow<String?> = _pendingClipboard
    // BEL sayacı: her uzak zilde artar — UI değişimi izleyip haptic verir.
    private val _bellCount = MutableStateFlow(0)
    val bellCount: StateFlow<Int> = _bellCount

    init {
        buffer.onTitle = { _windowTitle.value = it }
        buffer.onClipboard = { _pendingClipboard.value = it }
        buffer.onBell = { _bellCount.value += 1 }
    }

    fun consumeClipboard() { _pendingClipboard.value = null }

    // Bracketed paste: uzak taraf 2004 açtıysa çok satırlı yapıştırma
    // ESC[200~ ... ESC[201~ arasına sarılır (yanlışlıkla çalıştırma yok).
    val bracketedPaste: Boolean get() = buffer.bracketedPaste
    private val _frames = MutableStateFlow<List<String>>(emptyList())
    val frames: StateFlow<List<String>> = _frames
    var size = TerminalSize(80, 24)
        private set
    var badge = "SSH"
        private set

    private val history = ArrayDeque<String>()
    private var historyCursor = -1
    var historyCount = 0
        private set

    private var pendingBytes = ByteArray(0) // chunk sınırında bölünen UTF-8

    fun onFrame(f: TerminalFrame) {
        val all = pendingBytes + f.bytes
        if (all.isEmpty()) return
        val safe = utf8SafeEnd(all)
        pendingBytes = all.copyOfRange(safe, all.size)
        if (safe == 0) return
        buffer.feed(String(all, 0, safe, Charsets.UTF_8))
        val snap = buffer.snapshot()
        _lines.value = snap
        _frames.value = snap.map { it.text }
        _cursor.value = buffer.cursorPosition()
        badge = f.transport.name
    }

    // Sondaki eksik UTF-8 dizisinin başlangıcını döner (tamamsa size).
    private fun utf8SafeEnd(b: ByteArray): Int {
        var i = b.size
        var cont = 0
        while (i > 0 && b[i - 1].toInt() and 0xC0 == 0x80) { cont++; i-- }
        if (i == 0) return b.size
        val lead = b[i - 1].toInt()
        val need = when {
            lead and 0x80 == 0 -> 0
            lead and 0xE0 == 0xC0 -> 1
            lead and 0xF0 == 0xE0 -> 2
            lead and 0xF8 == 0xF0 -> 3
            else -> 0
        }
        return if (cont < need) i - 1 else b.size
    }

    fun setBadge(t: TerminalTransport) { badge = t.name }
    fun clear() { buffer.clear(); pendingBytes = ByteArray(0); _lines.value = emptyList(); _frames.value = emptyList(); _cursor.value = null }

    // Viewport ölçüsü değişti: buffer yeniden boyutlanır (üstten taşan satırlar
    // scrollback'e gider); PTY resize'ı UI katmanında transport'a gönderilir.
    fun setSize(newSize: TerminalSize) {
        if (newSize == size) return
        size = newSize
        buffer.setScreenSize(newSize.cols, newSize.rows)
        val snap = buffer.snapshot()
        _lines.value = snap
        _frames.value = snap.map { it.text }
        _cursor.value = buffer.cursorPosition()
    }

    val altScreenActive: Boolean get() = buffer.altScreenActive

    fun grow() { size = TerminalSize((size.cols + 10).coerceAtMost(200), size.rows) }
    fun shrink() { size = TerminalSize((size.cols - 10).coerceAtLeast(40), size.rows) }

    // Komut geçmişi: en yeni başta, dublikat ardışık giriş yok, 100 kayıt tavan.
    fun pushHistory(cmd: String) {
        val c = cmd.trimEnd('\n')
        if (c.isBlank()) return
        history.remove(c)
        history.addFirst(c)
        while (history.size > 100) history.removeLast()
        historyCount = history.size
        historyCursor = -1
    }

    // Geçmişte gezinme: yukarı = daha eski (null = zaten en eski),
    // aşağı = daha yeni (geçmiş dışına çıkınca boş string).
    fun historyOlder(): String? {
        if (history.isEmpty() || historyCursor >= history.size - 1) return null
        historyCursor++
        return history.elementAt(historyCursor)
    }

    fun historyNewer(): String {
        if (historyCursor < 0) return ""
        historyCursor--
        return if (historyCursor < 0) "" else history.elementAt(historyCursor)
    }
}
