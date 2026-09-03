// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Cihanda cbssh/SSHJ bağlanana kadar headless + JVM testleri besleyen sahte
// transport: PTY yankısı yapar, resize'ları kaydeder, kapatınca durur.
class FakeSshTransport : SshTransport {
    private var size = TerminalSize(80, 24)
    private var opened = false
    private val outbox = ArrayDeque<TerminalFrame>()
    val resizes = mutableListOf<TerminalSize>()
    override val transport = TerminalTransport.SSH
    private var closed = false

    override suspend fun openPty(term: String, size: TerminalSize) {
        check(!closed)
        opened = true
        this.size = size
        outbox.add(TerminalFrame("[$term ${size.cols}x${size.rows}]\n".toByteArray(), transport))
    }

    override suspend fun send(input: TerminalInput) {
        check(opened && !closed)
        when (input) {
            is TerminalInput.Text -> outbox.add(TerminalFrame(("> " + input.s).toByteArray(), transport))
            is TerminalInput.Key -> outbox.add(TerminalFrame(("[key:${input.code}]").toByteArray(), transport))
            is TerminalInput.Resize -> {
                size = input.size
                resizes.add(size)
                outbox.add(TerminalFrame(("[resize ${size.cols}x${size.rows}]").toByteArray(), transport))
            }
        }
    }

    override suspend fun read(): TerminalFrame {
        check(opened)
        return outbox.removeFirstOrNull() ?: TerminalFrame(ByteArray(0), transport)
    }

    override suspend fun resize(size: TerminalSize) = send(TerminalInput.Resize(size))
    override fun close() { closed = true }
}

// Ekran ViewModel'i: frame listesi (scrollback sınırlı) + giriş + boyut + transport rozeti.
class TerminalViewModel(val session: SessionId) {
    private val _frames = MutableStateFlow<List<String>>(emptyList())
    val frames: StateFlow<List<String>> = _frames
    var size = TerminalSize(80, 24)
        private set
    var badge = "SSH"
        private set

    fun onFrame(f: TerminalFrame) {
        val text = f.bytes.decodeToString()
        if (text.isEmpty()) return
        _frames.value = (_frames.value + text).takeLast(5000) // scrollback sınırı
        badge = f.transport.name
    }

    fun grow() { size = TerminalSize((size.cols + 10).coerceAtMost(200), size.rows) }
    fun shrink() { size = TerminalSize((size.cols - 10).coerceAtLeast(40), size.rows) }
}
