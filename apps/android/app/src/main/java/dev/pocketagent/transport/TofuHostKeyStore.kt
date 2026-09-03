// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import java.io.File
import java.util.Base64

// P04: TOFU pinning, separate from platform known_hosts. One line per host:
//   host port algorithm base64(key)
// A pinned key that changes on reconnect is a hard-stop (HostKeyChanged),
// never a silent accept and never a fallback to another transport.
class TofuHostKeyStore(private val file: File) {

    @Synchronized
    fun lookup(host: String, port: Int): PresentedKey? = readAll()
        .firstOrNull { it.host == host && it.port == port }

    @Synchronized
    fun pin(key: PresentedKey) {
        val rest = readAll().filterNot { it.host == key.host && it.port == key.port }
        file.parentFile?.mkdirs()
        file.writeText((rest + key).joinToString("\n") { serialize(it) } + "\n")
    }

    @Synchronized
    fun forget(host: String, port: Int) {
        val rest = readAll().filterNot { it.host == host && it.port == port }
        file.parentFile?.mkdirs()
        file.writeText(rest.joinToString("\n") { serialize(it) } + if (rest.isEmpty()) "" else "\n")
    }

    @Synchronized
    fun all(): List<PresentedKey> = readAll()

    private fun serialize(k: PresentedKey): String =
        listOf(k.host, k.port, k.algorithm, Base64.getEncoder().encodeToString(k.encoded)).joinToString(" ")

    private fun readAll(): List<PresentedKey> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val p = line.trim().split(" ")
            if (p.size != 4) return@mapNotNull null
            val port = p[1].toIntOrNull() ?: return@mapNotNull null
            val enc = runCatching { Base64.getDecoder().decode(p[3]) }.getOrNull() ?: return@mapNotNull null
            PresentedKey(p[0], port, p[2], enc)
        }
    }
}
