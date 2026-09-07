// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.net

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

// P04: Easy Pair istemcisi. QR (`pa1|<backend>|<kod>|…`) veya elle girilen
// XXXX-XXXX kodu → backend claim → tek-kullanım SSH payload'u.
// Payload yalnız claim'de döner; 5dk TTL + 409 tek-kullanım garantisi backend'de.
object PairingClient {

    data class Result(
        val sshHost: String,
        val sshPort: Int,
        val sshUser: String,
        val privateKeyPem: String,
    )

    sealed class Failure(val msg: String) {
        class BadCode : Failure("Kod tanınmadı veya süresi dolmuş (5dk)")
        class AlreadyClaimed : Failure("Bu kod başka bir cihazda kullanılmış")
        class Network(msg: String) : Failure(msg)
        class BadPayload(val body: String = "") : Failure("Payload çözülemedi: " + body.replace(Regex("[^ -~]"), "?").take(100))
    }

    // pa1|backend|kod|... — QR içeriğinden backend URL + kod çıkarır.
    // Kullanıcının elle girdiği XXXX-XXXX de kabul (backend parametresi kullanılır).
    fun parseQr(text: String, fallbackBackend: String): Pair<String, String>? {
        val t = text.trim()
        if (t.startsWith("pa1|")) {
            val parts = t.split("|")
            if (parts.size >= 3 && parts[1].isNotBlank() && parts[2].isNotBlank()) {
                val code = normalizeCode(parts[2]) ?: return null
                return parts[1] to code
            }
            return null
        }
        val code = normalizeCode(t)
        return if (code != null && fallbackBackend.isNotBlank()) fallbackBackend to code else null
    }

    fun normalizeCode(raw: String): String? {
        val c = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (c.length != 8) return null
        return c.substring(0, 4) + "-" + c.substring(4)
    }

    fun claim(backendUrl: String, code: String, deviceId: String): Result {
        val url = URL(backendUrl.trimEnd('/') + "/v1/pairing-sessions/" + code + "/claim")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Tenant", "pairing")
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write("""{"device_id":"$deviceId"}""".toByteArray()) }
            return when (conn.responseCode) {
                200 -> parsePayload(conn.inputStream.readBytes().decodeToString())
                404 -> throw FailureException(Failure.BadCode())
                409 -> throw FailureException(Failure.AlreadyClaimed())
                else -> throw FailureException(Failure.Network("backend: HTTP ${conn.responseCode}"))
            }
        } catch (e: FailureException) {
            throw e
        } catch (e: Exception) {
            throw FailureException(Failure.Network("backend erişilemedi: ${e.message}"))
        } finally {
            conn.disconnect()
        }
    }

    private fun parsePayload(body: String): Result {
        try {
            val o = JSONObject(body)
            val p = JSONObject(o.getString("payload"))
            return Result(
                sshHost = p.getString("ssh_host"),
                sshPort = p.optInt("ssh_port", 22),
                sshUser = p.getString("ssh_user"),
                privateKeyPem = p.getString("private_key"),
            )
        } catch (e: Exception) {
            throw FailureException(Failure.BadPayload(body))
        }
    }

    class FailureException(val failure: Failure) : Exception(failure.msg)
}
