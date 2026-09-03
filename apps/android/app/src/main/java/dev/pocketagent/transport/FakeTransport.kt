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

// Ekran ViewModel'i: scrollback (sınırlı), giriş, boyut, transport rozeti.
// Frame'ler ANSI'dan arındırılarak saklanır; her frame bir liste elemanı.
class TerminalViewModel(val session: SessionId) {
    private val _frames = MutableStateFlow<List<String>>(emptyList())
    val frames: StateFlow<List<String>> = _frames
    var size = TerminalSize(80, 24)
        private set
    var badge = "SSH"
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
}
