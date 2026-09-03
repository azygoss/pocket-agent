// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SshjConnector
import dev.pocketagent.transport.TerminalInput
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.transport.UnknownHostKeyException
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

// Canlı SSH doğrulaması — varsayılan kapalı. Çalıştırmak için:
//   PA_LIVE_SSH=1 PA_LIVE_USER=pa-dev PA_LIVE_PEM=/path/id_ed25519 \
//   ./gradlew :app:testDebugUnitTest --tests dev.pocketagent.SshjLiveTest
// SSHJ saf Java olduğu için JVM'de gerçek sshd'ye bağlanır.
class SshjLiveTest {
    @Test fun liveLocalhostSsh() = runBlocking {
        assumeTrue("set PA_LIVE_SSH=1 to run", System.getenv("PA_LIVE_SSH") == "1")
        val user = System.getenv("PA_LIVE_USER") ?: "pa-dev"
        val pemPath = System.getenv("PA_LIVE_PEM") ?: error("PA_LIVE_PEM missing")
        val pem = File(pemPath).readText()

        val kh = File.createTempFile("khst", null).apply { delete() }
        val store = TofuHostKeyStore(kh)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val conn = SavedConnection("live", "127.0.0.1", 22, user, "ram:pem")

        // 1) İlk bağlantı: bilinmeyen host anahtarı hard-stop vermeli.
        try {
            SshjConnector(store, scope).open(conn, Secret.PemKey(pem), TerminalSize(80, 24))
            fail("expected UnknownHostKeyException on first connect")
        } catch (e: UnknownHostKeyException) {
            store.pin(e.presented)
        }

        // 2) Pinli ikinci bağlantı başarılı; gerçek komut çıktısı okunur.
        val t = SshjConnector(store, scope).open(conn, Secret.PemKey(pem), TerminalSize(80, 24))
        t.send(TerminalInput.Text("echo PA_ALIVE_$((40+2)) && whoami\n"))
        val sb = StringBuilder()
        withTimeout(15_000) {
            while (!sb.contains("PA_ALIVE_42")) {
                sb.append(t.read().bytes.decodeToString())
            }
        }
        assertTrue(sb.toString().contains("PA_ALIVE_42"))
        assertTrue(sb.toString().contains(user))
        t.close()
    }
}
