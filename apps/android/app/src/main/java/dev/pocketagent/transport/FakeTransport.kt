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

    override suspend fun resize(size: TerminalSize) = send(TerminalInput.Resize(size))
    override fun close() { closed = true; outbox.close() }
}

// Ekran ViewModel'i: scrollback (sınırlı), giriş, boyut, transport rozeti,
// komut geçmişi (yukarı/aşağı gezinme). Frame'ler ANSI'dan arındırılır.
class TerminalViewModel(val session: SessionId) {
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

    fun onFrame(f: TerminalFrame) {
        val text = Ansi.strip(f.bytes.decodeToString())
        if (text.isBlank()) return
        _frames.value = (_frames.value + text).takeLast(5000) // scrollback sınırı
        badge = f.transport.name
    }

    fun setBadge(t: TerminalTransport) { badge = t.name }
    fun clear() { _frames.value = emptyList() }

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
