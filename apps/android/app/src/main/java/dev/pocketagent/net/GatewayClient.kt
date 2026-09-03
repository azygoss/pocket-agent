// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.net

import dev.pocketagent.transport.jailOk
import dev.pocketagent.transport.loopbackOk
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// P11/P15: gateway reachable ONLY via SSH local-forward to 127.0.0.1:24543.
// Client enforces the same jail + loopback rules as host (defense in depth).
class GatewayClient(private val localPort: Int = 24543, private val token: String) {
    fun fileUrl(rel: String): URL {
        require(jailOk(rel)) { "traversal rejected" }
        return URL("http://127.0.0.1:$localPort/file/$rel")
    }

    fun readFile(rel: String, maxBytes: Int = 1 shl 20): String {
        val c = fileUrl(rel).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 5000
        c.readTimeout = 5000
        require(c.responseCode == 200) { "gateway ${c.responseCode}" }
        val text = BufferedReader(InputStreamReader(c.inputStream)).readText()
        return if (text.length > maxBytes) text.substring(0, maxBytes) else text
    }

    fun previewUrl(host: String, port: Int, path: String): URL {
        require(loopbackOk(host)) { "SSRF rejected" }
        require(port in 1..65535 && path.startsWith("/"))
        return URL("http://127.0.0.1:$localPort/preview?h=$host&p=$port&path=$path")
    }
}
