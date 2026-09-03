// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P16 deep links: pocketagent://tmux?... pocketagent://herdr?...
// Unsigned params must never execute shell.
data class DeepLink(val provider: SessionProvider, val action: String)

fun parseDeepLink(uri: String): DeepLink? {
    if (!uri.startsWith("pocketagent://")) return null
    val rest = uri.removePrefix("pocketagent://").substringBefore("?")
    return when (rest) {
        "tmux" -> DeepLink(SessionProvider.TMUX, "open")
        "herdr" -> DeepLink(SessionProvider.HERDR, "open")
        else -> null
    }
}

// P11 parity: strict jail (any ".." rejected), loopback-only preview host.
fun jailOk(rel: String): Boolean {
    if (rel.startsWith("/")) return false
    if (rel.split("/").contains("..")) return false
    return true
}

fun loopbackOk(host: String): Boolean {
    if (host == "localhost") return true
    if (host == "127.0.0.1" || host == "::1") return true
    if (host.startsWith("127.")) {
        val tail = host.removePrefix("127.").split(".")
        if (tail.size == 3 && tail.all { it.toIntOrNull() in 0..255 }) return true
    }
    return false
}
