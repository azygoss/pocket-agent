// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.android.approvalsToNotify
import dev.pocketagent.net.BackendEvent
import dev.pocketagent.net.PairingClient
import dev.pocketagent.net.backendProblem
import dev.pocketagent.net.normalizeBackendUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Backend adresi, okunur ağ hataları, onay bildirimi süzgeci ve anahtarsız
// eşleme yanıtı.
@RunWith(RobolectricTestRunner::class)
class NetLogicTest {
    @Test fun backendUrlIsNormalizedAndCleartextRejected() {
        assertEquals("https://agent.example.com" to null, normalizeBackendUrl(" agent.example.com/ "))
        assertEquals("https://1-2-3-4.nip.io" to null, normalizeBackendUrl("https://1-2-3-4.nip.io"))
        assertEquals("http://127.0.0.1:8080" to null, normalizeBackendUrl("http://127.0.0.1:8080"))
        assertEquals("" to null, normalizeBackendUrl("  "))
        // Telefon uzak adrese şifresiz HTTP açmaz: kaydetmeden önce söylenir.
        assertTrue(normalizeBackendUrl("http://128.140.70.117:8080").second!!.contains("https://"))
        assertNotNull(normalizeBackendUrl("https://bad host").second)
        assertNotNull(normalizeBackendUrl("ftp://x.example.com").second)
    }

    @Test fun networkErrorsBecomeActionableText() {
        assertTrue(backendProblem(java.net.UnknownHostException("x")).startsWith("Adres bulunamadı"))
        assertTrue(backendProblem(java.io.IOException("wrap", javax.net.ssl.SSLHandshakeException("chain"))).contains("sertifika"))
        assertTrue(backendProblem(java.io.IOException("Cleartext HTTP traffic to h not permitted")).contains("https://"))
        assertEquals("Backend HTTP 404 döndü. Adres doğru mu?", backendProblem(IllegalArgumentException("events 404")))
        // Port numarası HTTP kodu sanılmaz.
        assertTrue(backendProblem(java.net.ConnectException("Failed to connect to /1.2.3.4:443")).contains("reddetti"))
    }

    private fun ev(id: String, cat: String, agoMs: Long, now: Long) = BackendEvent(
        id, "h:1", "tmux:pa-1", "claude", cat, "Bash: rm", "d", "1",
        java.time.Instant.ofEpochMilli(now - agoMs).toString(), java.time.Instant.ofEpochMilli(now + 3_600_000).toString(),
    )

    // Uygulama yeniden açılınca 24 saatlik geçmiş tekrar bildirilmez; yalnız
    // yeni ve taze onaylar.
    @Test fun onlyFreshUnknownApprovalsNotify() {
        val now = System.currentTimeMillis()
        val events = listOf(
            ev("seen", "APPROVAL_REQUIRED", 60_000, now),
            ev("old", "APPROVAL_REQUIRED", 3_600_000, now),
            ev("done", "TASK_COMPLETE", 10_000, now),
            ev("new", "APPROVAL_REQUIRED", 5_000, now),
        )
        assertEquals(listOf("new"), approvalsToNotify(events, setOf("seen"), now).map { it.eventId })
    }

    // Tek isteklik yerel HTTP sunucusu (Android test classpath'inde JDK
    // HttpServer yok): claim isteğini okur, verilen yükle yanıtlar.
    private fun serve(payload: String, onBody: (String) -> Unit = {}): java.net.ServerSocket {
        val ss = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        kotlin.concurrent.thread(isDaemon = true) {
            runCatching {
                ss.accept().use { sock ->
                    val input = sock.getInputStream().bufferedReader()
                    var len = 0
                    while (true) {
                        val l = input.readLine() ?: break
                        if (l.isEmpty()) break
                        if (l.startsWith("Content-Length:", ignoreCase = true)) len = l.substringAfter(":").trim().toInt()
                    }
                    val req = StringBuilder()
                    repeat(len) { req.append(input.read().toChar()) }
                    onBody(req.toString())
                    val body = """{"state":"CLAIMED","device_token":"pad_x","payload":${org.json.JSONObject.quote(payload)}}""".toByteArray()
                    sock.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                }
            }
        }
        return ss
    }

    @Test fun keylessClaimSendsOnlyPublicKey() {
        var sent = ""
        val payload = """{"ssh_host":"h","ssh_port":2222,"ssh_user":"root","host_keys":["SHA256:abc"],"host_name":"lab"}"""
        serve(payload) { sent = it }.use { s ->
            val r = PairingClient.claim("http://127.0.0.1:${s.localPort}", "ABCD-EFGH", "dev", "Pixel", "ssh-ed25519 AAAAC3 test")
            assertEquals("root", r.sshUser)
            assertEquals(2222, r.sshPort)
            assertEquals("pad_x", r.deviceToken)
            assertEquals(listOf("SHA256:abc"), r.hostKeys)
            assertEquals("lab", r.hostName)
        }
        val o = org.json.JSONObject(sent)
        assertEquals("ssh-ed25519 AAAAC3 test", o.getString("public_key"))
        assertEquals("Pixel", o.getString("device_name"))
        assertTrue(!sent.contains("PRIVATE"))
    }

    // Eski host (private_key taşıyan yük) reddedilir: anahtar backend'den alınmaz.
    @Test fun legacyPrivateKeyPayloadRejected() {
        serve("""{"ssh_host":"h","ssh_user":"root","private_key":"k"}""").use { s ->
            try {
                PairingClient.claim("http://127.0.0.1:${s.localPort}", "ABCD-EFGH", "dev", "Pixel", "ssh-ed25519 AAAA x")
                org.junit.Assert.fail("eski yük kabul edilmemeli")
            } catch (e: PairingClient.FailureException) {
                assertTrue(e.failure is PairingClient.Failure.OldHost)
            }
        }
    }
}
