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

// Telefonun şifresiz HTTP'ye izin verdiği adresler (network_security_config).
private val CLEARTEXT_OK = setOf("localhost", "127.0.0.1", "10.0.2.2")

// Yazılan backend adresini düzeltir: şema yoksa https:// eklenir, sondaki /
// atılır. İkinci değer kullanıcıya gösterilecek hata (null = geçerli).
fun normalizeBackendUrl(raw: String): Pair<String, String?> {
    var u = raw.trim().trimEnd('/')
    if (u.isEmpty()) return "" to null
    if (!u.contains("://")) u = "https://$u"
    val uri = runCatching { java.net.URI(u) }.getOrNull()
    val host = uri?.host
    return u to when {
        uri == null || host.isNullOrBlank() || uri.scheme !in setOf("http", "https") ->
            "Geçerli bir adres değil (örn. https://agent.ornek.com)."
        uri.scheme == "http" && host !in CLEARTEXT_OK ->
            "Telefon şifresiz HTTP'ye bağlanmaz. Adresi https:// ile yaz; domain yoksa 1-2-3-4.nip.io biçimi çalışır."
        else -> null
    }
}

// Ağ hatasını, kullanıcıya ne yapacağını söyleyen kısa metne çevirir.
fun backendProblem(e: Throwable): String {
    val chain = generateSequence(e) { it.cause }.take(8).toList()
    val text = chain.joinToString(" ") { it.message.orEmpty() }
    // BackendClient.events'in require mesajı ("events 404") ya da "HTTP 502".
    val http = Regex("(?:events|HTTP) ([45]\\d\\d)").find(text)?.groupValues?.get(1)
    return when {
        text.contains("Cleartext", ignoreCase = true) ->
            "Telefon şifresiz HTTP'ye bağlanmaz; adresi https:// ile yaz."
        chain.any { it is java.net.UnknownHostException } ->
            "Adres bulunamadı. Yazımı ve internet bağlantını kontrol et."
        chain.any { it is javax.net.ssl.SSLException || it is java.security.cert.CertificateException } ->
            "HTTPS sertifikası doğrulanamadı. Sunucuda geçerli bir sertifika olmalı (Caddy, DOMAIN)."
        chain.any { it is java.net.SocketTimeoutException } ->
            "Backend zamanında yanıt vermedi. Sunucu açık mı, 443 portu dışarıya açık mı?"
        chain.any { it is java.net.ConnectException } ->
            "Backend bağlantıyı reddetti. Sunucu çalışıyor mu?"
        http != null -> "Backend HTTP $http döndü. Adres doğru mu?"
        else -> e.message ?: e.javaClass.simpleName
    }
}

class BackendClient(
    private val baseUrl: String,
    // Cihaza özgü bearer token (eşlemede verilir; sunucuda yalnız hash'i).
    private val token: String,
    private val timeoutMs: Int = 8_000,
) {
    private fun conn(path: String, method: String = "GET"): HttpURLConnection {
        val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/json")
        return c
    }

    fun health(): Boolean = check() == null

    // null = erişilebilir; değilse okunur neden.
    fun check(): String? = try {
        val code = conn("/v1/healthz").responseCode
        when {
            code != 200 -> "Backend HTTP $code döndü. Adres doğru mu?"
            token.isBlank() -> null
            else -> when (val w = conn("/v1/whoami").responseCode) {
                200 -> null
                401 -> "Cihaz token'ı geçersiz veya iptal edilmiş — host'ta pocket-agent pair ile yeniden eşle."
                else -> "Backend HTTP $w döndü."
            }
        }
    } catch (e: Exception) {
        backendProblem(e)
    }

    fun events(after: String? = null): List<BackendEvent> {
        val path = if (after.isNullOrEmpty()) "/v1/events" else "/v1/events?after=$after"
        val c = conn(path)
        if (c.responseCode == 401) throw IllegalStateException("Cihaz token'ı geçersiz — yeniden eşle")
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
            .toString()
        OutputStreamWriter(c.outputStream).use { it.write(payload) }
        return c.responseCode
    }

    // Kullanım snapshot'ları (P13: türetilmiş, ham transcript asla). Şimdilik
    // backend boş dizi döndürür — host raporlama bağlanınca dolar.
    fun usages(): JSONArray {
        val c = conn("/v1/usages")
        require(c.responseCode == 200) { "usages ${c.responseCode}" }
        val body = BufferedReader(InputStreamReader(c.inputStream)).readText()
        return JSONArray(body)
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
