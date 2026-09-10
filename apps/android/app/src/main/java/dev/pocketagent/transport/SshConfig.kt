// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// ~/.ssh/config içe aktarımı: OpenSSH config'in pratik alt kümesi.
// Wildcard'lı/negatif Host blokları atlanır; HostName/Port/User/IdentityFile
// toplanır. IdentityFile varsa PEM akışına, yoksa parola akışına yönlendirir.
data class SshConfigEntry(
    val name: String,
    val host: String,
    val port: Int,
    val user: String,
    val identityFile: String?,
)

object SshConfig {
    fun parse(text: String): List<SshConfigEntry> {
        val out = mutableListOf<SshConfigEntry>()
        var name: String? = null
        var host = ""
        var port = 22
        var user = ""
        var identity: String? = null
        fun flush() {
            val n = name ?: return
            if (n.contains('*') || n.contains('?') || n.startsWith('!')) return
            val h = host.ifBlank { n }
            out += SshConfigEntry(n, h, port, user, identity)
        }
        for (raw in text.lines()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val kv = line.split(Regex("[=\\s]"), 2).map { it.trim() }
            if (kv.size < 2) continue
            when (kv[0].lowercase()) {
                "host" -> {
                    flush()
                    name = kv[1]; host = ""; port = 22; user = ""; identity = null
                }
                "hostname" -> host = kv[1]
                "port" -> port = kv[1].toIntOrNull() ?: 22
                "user" -> user = kv[1]
                "identityfile" -> identity = kv[1]
            }
        }
        flush()
        return out
    }

    fun toConnection(e: SshConfigEntry): SavedConnection = SavedConnection(
        name = e.name,
        host = e.host,
        port = e.port,
        user = e.user.ifBlank { "root" },
        credentialRef = if (e.identityFile != null) "ram:pem" else "ram:password",
        transportOrder = listOf(TerminalTransport.SSH),
    )
}
