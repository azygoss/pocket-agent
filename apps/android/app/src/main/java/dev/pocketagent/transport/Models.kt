// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P01 contract mirrors (Kotlin side). Golden fixture: protocol/fixtures/golden-v1.json.
@JvmInline value class HostId(val v: String)
@JvmInline value class DeviceId(val v: String)
@JvmInline value class ProfileId(val v: String)
@JvmInline value class SessionId(val v: String)
@JvmInline value class EventId(val v: String)
@JvmInline value class ApprovalId(val v: String)
@JvmInline value class CommandId(val v: String)
@JvmInline value class EventCursor(val v: String)
@JvmInline value class Revision(val v: String)

enum class TerminalTransport { SSH, MOSH, ET }
enum class SessionProvider { SHELL, TMUX, ZELLIJ, HERDR }
enum class ApprovalState { PENDING, RESOLVED, EXPIRED }
enum class ConnectionState { CONNECTING, ACTIVE, SUSPENDED, RECONNECTING, CLOSED, FAILED }

data class HostCapabilities(
    val ssh: Boolean = true,
    val mosh: Boolean = false,
    val et: Boolean = false,
    val tmux: Boolean = false,
    val zellij: Boolean = false,
    val herdr: Boolean = false,
    val gateway: Boolean = false,
    val chat: Boolean = false,
)

// P06: auth/host-key errors must NOT fall back. Others follow preferred order.
data class TransportPolicy(val preferredOrder: List<TerminalTransport> = listOf(TerminalTransport.MOSH, TerminalTransport.ET, TerminalTransport.SSH))

sealed interface TransportFailure {
    data object AuthFailed : TransportFailure
    data object HostKeyChanged : TransportFailure
    data class Network(val reason: String) : TransportFailure
    data class MissingServer(val what: String) : TransportFailure
}

fun selectNext(current: TerminalTransport?, failure: TransportFailure, policy: TransportPolicy, caps: HostCapabilities): TerminalTransport? {
    if (failure is TransportFailure.AuthFailed || failure is TransportFailure.HostKeyChanged) return null // hard-stop
    val order = policy.preferredOrder.filter {
        when (it) {
            TerminalTransport.SSH -> caps.ssh
            TerminalTransport.MOSH -> caps.mosh
            TerminalTransport.ET -> caps.et
        }
    }
    if (current == null) return order.firstOrNull()
    val idx = order.indexOf(current)
    return if (idx >= 0 && idx + 1 < order.size) order[idx + 1] else null
}

// P01 golden parity: message<=256, whitelist source/category (same lists as Go).
fun validateEventSummary(source: String, category: String, message: String): Boolean {
    val sources = setOf("claude","codex","opencode","cursor","kimi","grok","pi","omp","hermes","gemini","antigravity","qwen")
    val cats = setOf("APPROVAL_REQUIRED","TASK_COMPLETE","SESSION_STARTED","SESSION_ENDED","TOOL_RUNNING","TOOL_FINISHED","ERROR")
    return source in sources && category in cats && message.length <= 256
}
