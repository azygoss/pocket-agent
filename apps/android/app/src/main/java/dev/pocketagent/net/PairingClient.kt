// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.net

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

// P04: Easy Pair istemcisi, anahtarsız. QR (`pa2|<backend>|<kod>|…`) veya elle
// girilen XXXX-XXXX kodu → cihaz kendi ed25519 anahtarını üretir → backend'e
// yalnız PUBLIC key ile claim → host anahtarı onaylayıp authorized_keys'e
// ekler. Özel anahtar cihazdan hiç çıkmaz; backend'e de host'a da gitmez.
object PairingClient {

    data class Result(
        val sshHost: String,
        val sshPort: Int,
        val sshUser: String,
        // Bu cihaza özgü backend bearer token'ı (sunucuda yalnız hash'i var).
        val deviceToken: String,
        // Host'un SSH host key parmak izleri (SHA256:…); boşsa TOFU.
        val hostKeys: List<String> = emptyList(),
        val hostName: String? = null,
    )

    sealed class Failure(val msg: String) {
        class BadCode : Failure("Kod tanınmadı veya süresi dolmuş (5dk)")
        class AlreadyClaimed : Failure("Bu kod başka bir cihazda kullanılmış")
        class Rejected : Failure("Host eşleştirmeyi reddetti")
        class Timeout : Failure("Host'ta onay gelmedi (5dk). pocket-agent pair'i yeniden çalıştır.")
        class OldHost : Failure("Host CLI eski: anahtarsız eşleme için pocket-agent'ı güncelle")
        class Network(msg: String) : Failure(msg)
        class BadPayload(val body: String = "") : Failure("Payload çözülemedi: " + body.replace(Regex("[^ -~]"), "?").take(100))
    }

    // pa1|/pa2|backend|kod|... — QR içeriğinden backend URL + kod çıkarır.
    // Kullanıcının elle girdiği XXXX-XXXX de kabul (backend parametresi kullanılır).
    fun parseQr(text: String, fallbackBackend: String): Pair<String, String>? {
        val t = text.trim()
        if (t.startsWith("pa1|") || t.startsWith("pa2|")) {
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

    private fun open(url: String, method: String, token: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }

    fun claim(backendUrl: String, code: String, deviceId: String, deviceName: String, publicKey: String): Result {
        val conn = open(backendUrl.trimEnd('/') + "/v1/pairing-sessions/" + code.replace("-", "") + "/claim", "POST")
        conn.doOutput = true
        try {
            val body = JSONObject()
                .put("device_id", deviceId)
                .put("device_name", deviceName)
                .put("public_key", publicKey)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            return when (conn.responseCode) {
                200 -> parseClaim(conn.inputStream.readBytes().decodeToString())
                400 -> throw FailureException(Failure.OldHost())
                404 -> throw FailureException(Failure.BadCode())
                409 -> throw FailureException(Failure.AlreadyClaimed())
                429 -> throw FailureException(Failure.Network("Çok fazla deneme — bir dakika bekleyip tekrar dene"))
                else -> throw FailureException(Failure.Network("Backend HTTP ${conn.responseCode} döndü. Adres doğru mu?"))
            }
        } catch (e: FailureException) {
            throw e
        } catch (e: Exception) {
            throw FailureException(Failure.Network(backendProblem(e)))
        } finally {
            conn.disconnect()
        }
    }

    // Host kullanıcının onayını bekler: CONSUMED → anahtar yetkili.
    fun awaitApproval(
        backendUrl: String,
        code: String,
        deviceToken: String,
        timeoutMs: Long = 5 * 60_000L,
        pollMs: Long = 1_500L,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val c = open(backendUrl.trimEnd('/') + "/v1/pairing-sessions/" + code.replace("-", ""), "GET", deviceToken)
            try {
                when (c.responseCode) {
                    200 -> when (JSONObject(c.inputStream.readBytes().decodeToString()).optString("state")) {
                        "CONSUMED" -> return
                        "REJECTED" -> throw FailureException(Failure.Rejected())
                    }
                    401, 404 -> throw FailureException(Failure.Rejected())
                }
            } catch (e: FailureException) {
                throw e
            } catch (_: Exception) {
                // geçici ağ hatası: yeniden dene
            } finally {
                c.disconnect()
            }
            Thread.sleep(pollMs)
        }
        throw FailureException(Failure.Timeout())
    }

    fun parseClaim(body: String): Result {
        try {
            val o = JSONObject(body)
            val p = JSONObject(o.getString("payload"))
            if (p.has("private_key")) throw FailureException(Failure.OldHost())
            val keys = p.optJSONArray("host_keys")
            return Result(
                sshHost = p.getString("ssh_host"),
                sshPort = p.optInt("ssh_port", 22),
                sshUser = p.getString("ssh_user"),
                deviceToken = o.getString("device_token"),
                hostKeys = if (keys == null) emptyList() else (0 until keys.length()).map { keys.getString(it) },
                hostName = p.optString("host_name").ifBlank { null },
            )
        } catch (e: FailureException) {
            throw e
        } catch (e: Exception) {
            throw FailureException(Failure.BadPayload(body))
        }
    }

    class FailureException(val failure: Failure) : Exception(failure.msg)
}
