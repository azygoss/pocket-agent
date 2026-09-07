// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.net.PairingClient
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SshjConnector
import dev.pocketagent.transport.TerminalController
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

// P04 QR/kod parse birim testleri.
class PairingParseTest {
    @Test fun qrPayloadParses() {
        val (backend, code) = PairingClient.parseQr("pa1|http://h:8080|fqat-r6dw|extra", "")!!
        assertEquals("http://h:8080", backend)
        assertEquals("FQAT-R6DW", code)
    }

    @Test fun manualCodeParsesWithFallbackBackend() {
        val (backend, code) = PairingClient.parseQr("abcd ef23", "http://h:8080")!!
        assertEquals("http://h:8080", backend)
        assertEquals("ABCD-EF23", code)
    }

    @Test fun junkRejected() {
        assertNull(PairingClient.parseQr("hello", "http://h:8080"))
        assertNull(PairingClient.parseQr("pa1|only", "http://h:8080"))
        assertNull(PairingClient.parseQr("ABCDEFGH", "")) // backend yoksa elle kod işlemez
    }
}

// P04 uçtan uca CANLI test: host CLI pair → backend claim → SSH connect.
// Env: PA_LIVE_SSH=1 PA_LIVE_PAIR_BIN=/tmp/pa-hook PA_LIVE_BACKEND=... PA_LIVE_USER=pa-dev
// POCKET_HOME=/home/pa-dev (CLI marker'ı gerçek authorized_keys'e yazar; test temizler).
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class PairingLiveTest {
    @Test fun fullPairingFlow() { runBlocking {
        val bin = System.getenv("PA_LIVE_PAIR_BIN") ?: "/tmp/pa-hook"
        val backend = System.getenv("PA_LIVE_BACKEND") ?: "http://127.0.0.1:8080"
        val usr = System.getenv("PA_LIVE_USER") ?: "pa-dev"
        assumeTrue(File(bin).exists() && System.getenv("PA_LIVE_SSH") == "1")

        // 1) host: pair (QR payload + backend session + authorized_keys marker)
        val p = ProcessBuilder(bin, "pair", "--backend", backend, "--host", "127.0.0.1", "--user", usr)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        val code = Regex("Kod: ([A-Z0-9]{4}-[A-Z0-9]{4})").find(out)?.groupValues?.get(1)
        assertNotNull("pair çıktısında kod yok:\n$out", code)

        // 2) telefon: claim → payload
        val r = PairingClient.claim(backend, code!!, "live-test-device")
        assertEquals(usr, r.sshUser)
        assertTrue(r.privateKeyPem.contains("BEGIN OPENSSH PRIVATE KEY"))

        // 3) payload anahtarıyla SSH bağlan (TOFU pin gerekli)
        val store = TofuHostKeyStore(File.createTempFile("kh-pair", ".db"))
        val connector = SshjConnector(store, CoroutineScope(Dispatchers.IO))
        val scope = CoroutineScope(Dispatchers.IO)
        val c = TerminalController(scope, connector, store)
        c.connect(
            SavedConnection("pair", "127.0.0.1", 22, usr, "keystore", id = "pair-1"),
            Secret.PemKey(r.privateKeyPem),
        )
        // Bilinmeyen host-key diyaloğu düşerse pinle ve tekrar dene
        withTimeout(5000) {
            while (c.state.value != ConnectionState.ACTIVE) {
                c.pendingHostKey.value?.let { c.acceptHostKeyAndReconnect() }
                if (c.state.value == ConnectionState.FAILED) break
                delay(100)
            }
        }
        assertEquals("payload anahtarıyla bağlantı kurulamadı", ConnectionState.ACTIVE, c.state.value)
        c.disconnect()

        // 4) ikinci cihaz aynı kodu alamaz (tek-kullanım)
        try {
            PairingClient.claim(backend, code, "another-device")
            fail("ikinci claim 409 beklerdi")
        } catch (e: PairingClient.FailureException) {
            assertTrue(e.failure is PairingClient.Failure.AlreadyClaimed)
        }

        // 5) temizlik: marker satırını kaldır
        val pu = ProcessBuilder(bin, "unpair", code)
        pu.environment()["POCKET_HOME"] = System.getenv("POCKET_HOME") ?: "/home/$usr"
        pu.inheritIO().start().waitFor()
    }
 } }
