// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.net

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

// P13: backend client — yalnız özet event'ler (≤256ch, 24h TTL) ve onay
// kararları geçer. Terminal/diff/dosya/ses asla bu kanala girmez (plan §2.2).
data class BackendEvent(
    val eventId: String,
    val host: String,
    val session: String,
    val source: String,
    val category: String,
    val message: String,
    val digest: String,
    val revision: String,
    val createdAt: String,
    val expiresAt: String,
)

class BackendClient(
    private val baseUrl: String,
    private val tenant: String,
    private val timeoutMs: Int = 8_000,
) {
    private fun conn(path: String, method: String = "GET"): HttpURLConnection {
        val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("X-Tenant", tenant)
        c.setRequestProperty("Accept", "application/json")
        return c
    }

    fun health(): Boolean = runCatching {
        conn("/v1/healthz").responseCode == 200
    }.getOrDefault(false)

    fun events(after: String? = null): List<BackendEvent> {
        val path = if (after.isNullOrEmpty()) "/v1/events" else "/v1/events?after=$after"
        val c = conn(path)
        require(c.responseCode == 200) { "events ${c.responseCode}" }
        val body = BufferedReader(InputStreamReader(c.inputStream)).readText()
        return parseEvents(body)
    }

    // 202 = karar kabul edildi; 409 = başka cihaz kazandı (CAS ilk-kazanan).
    fun approvalAction(approvalId: String, digest: String, revision: String, device: String): Int {
        val c = conn("/v1/approvals/$approvalId/actions", "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        val payload = JSONObject()
            .put("digest", digest)
            .put("revision", revision)
            .put("device", device)
            .toString()
        OutputStreamWriter(c.outputStream).use { it.write(payload) }
        return c.responseCode
    }

    companion object {
        fun parseEvents(body: String): List<BackendEvent> {
            val arr = JSONArray(body)
            return (0 until arr.length()).map { i ->
                val o: JSONObject = arr.getJSONObject(i)
                BackendEvent(
                    eventId = o.optString("EventID"),
                    host = o.optString("OpaqueHost"),
                    session = o.optString("OpaqueSess"),
                    source = o.optString("Source"),
                    category = o.optString("Category"),
                    message = o.optString("Message"),
                    digest = o.optString("Digest"),
                    revision = o.optString("Revision"),
                    createdAt = o.optString("CreatedAt"),
                    expiresAt = o.optString("ExpiresAt"),
                )
            }
        }
    }
}
