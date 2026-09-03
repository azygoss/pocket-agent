// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P06/P08: SSH-first transport surface. cbssh/SSHJ implementasyonu bu arayüze
// bağlanır; auth/host-key hataları fallback üretmez (selectNext null döner).
data class TerminalSize(val cols: Int, val rows: Int) {
    init { require(cols in 1..500 && rows in 1..200) { "bad pty size" } }
}

sealed interface TerminalInput {
    data class Text(val s: String) : TerminalInput
    data class Key(val code: Int) : TerminalInput // Ctrl/Esc/Tab/Alt/oklar/F1-F12
    data class Resize(val size: TerminalSize) : TerminalInput
}

data class TerminalFrame(val bytes: ByteArray, val transport: TerminalTransport) {
    override fun equals(other: Any?): Boolean = other is TerminalFrame && bytes.contentEquals(other.bytes) && transport == other.transport
    override fun hashCode(): Int = bytes.contentHashCode() * 31 + transport.hashCode()
}

interface SshTransport : AutoCloseable {
    suspend fun openPty(term: String = "xterm-256color", size: TerminalSize)
    suspend fun send(input: TerminalInput)
    suspend fun read(): TerminalFrame
    suspend fun resize(size: TerminalSize)
    val transport: TerminalTransport
}

// P08 orkestrasyon: policy + caps + failure -> sonraki transport (null = hard-stop).
class TransportManager(
    private val policy: TransportPolicy = TransportPolicy(),
    private val caps: HostCapabilities = HostCapabilities(ssh = true, mosh = true, et = true),
) {
    var current: TerminalTransport? = null
        private set

    fun start(): TerminalTransport? {
        current = selectNext(null, TransportFailure.Network("init"), policy, caps)
        return current
    }

    fun onFailure(f: TransportFailure): TerminalTransport? {
        current = selectNext(current, f, policy, caps)
        return current
    }
}
