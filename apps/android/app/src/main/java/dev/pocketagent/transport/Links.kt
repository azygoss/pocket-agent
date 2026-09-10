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

// pocketagent://add?host=h&user=u&port=p&name=n — dokümandan/host listesinden
// tek dokunuşla host ekleme. Parametreler URL-encoded; yalnız alan doldurur,
// asla bağlanmaz ya da komut çalıştırmaz.
fun parseAddHostLink(uri: String): SavedConnection? {
    if (!uri.startsWith("pocketagent://add")) return null
    val q = uri.substringAfter('?', "")
    val params = q.split('&').mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.take(i) to java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
    }.toMap()
    val host = params["host"] ?: return null
    val c = SavedConnection(
        name = params["name"]?.ifBlank { null } ?: "$host",
        host = host,
        port = params["port"]?.toIntOrNull() ?: 22,
        user = params["user"] ?: "",
        credentialRef = "ram:password",
        transportOrder = listOf(TerminalTransport.SSH),
    )
    return c.takeIf { it.host.isNotBlank() }
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
