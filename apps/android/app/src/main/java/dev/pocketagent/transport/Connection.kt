// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P05: SavedConnection carries NO secrets — only references + transport order.
data class SavedConnection(
    val name: String,
    val host: String,
    val port: Int,
    val user: String,
    val credentialRef: String,
    val transportOrder: List<TerminalTransport> = listOf(TerminalTransport.MOSH, TerminalTransport.ET, TerminalTransport.SSH),
    val jumpHost: String? = null,
    val moshUdpRange: String? = null,
    val etPort: Int = 2022,
    val agentForward: Boolean = false,
    val autoTmux: Boolean = false, // bağlanınca `tmux new-session -A -s main`
    val id: String = "",
    val lastConnectedAt: Long = 0L,
) {
    fun validate(): List<String> {
        val errs = mutableListOf<String>()
        if (name.isBlank()) errs += "name"
        if (host.isBlank()) errs += "host"
        if (port !in 1..65535) errs += "port"
        if (user.isBlank()) errs += "user"
        if (credentialRef.isBlank()) errs += "credentialRef"
        // Secret leak guard: ref must be a reference, never key material.
        if (credentialRef.contains("BEGIN") || credentialRef.length > 256) errs += "credentialRef:must-be-reference"
        return errs
    }
}

// P13 parity: approval binds eventId|digest|revision|decision|expiresAt|nonce.
fun approvalBind(eventId: String, digest: String, rev: String, decision: String, expiresAt: String, nonce: String): String =
    listOf(eventId, digest, rev, decision, expiresAt, nonce).joinToString("|")
