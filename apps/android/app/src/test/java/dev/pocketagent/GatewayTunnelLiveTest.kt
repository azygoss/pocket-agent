// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.net.GatewayClient
import dev.pocketagent.transport.GatewayTunnel
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SshjConnector
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.transport.UnknownHostKeyException
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Canlı P11 kanıtı: host gateway'e (127.0.0.1:24543) yalnız SSH direct-tcpip
// tüneli üzerinden erişim. VPS'te gateway çalışıyor olmalı:
//   POCKET_GATEWAY_TOKEN=tok123 pocket-agent gateway serve --root /tmp/pa-workspace
@RunWith(RobolectricTestRunner::class)
class GatewayTunnelLiveTest {
    @Test fun tunnelFetch() {
        runBlocking {
            val pemPath = System.getenv("PA_LIVE_PEM")
            val user = System.getenv("PA_LIVE_USER") ?: "pa-dev"
            assumeTrue(
                "live ssh yok",
                System.getenv("PA_LIVE_SSH") == "1" && pemPath != null && System.getenv("PA_LIVE_GW") == "1",
            )

            val store = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
            val scope = CoroutineScope(Dispatchers.IO)
            val connector = SshjConnector(store, scope)
            val conn = SavedConnection("live", "127.0.0.1", 22, user, "ram:pem")
            val pem = File(pemPath!!).readText()

            val t = try {
                connector.open(conn, Secret.PemKey(pem), TerminalSize(80, 24))
            } catch (e: UnknownHostKeyException) {
                store.pin(e.presented)
                connector.open(conn, Secret.PemKey(pem), TerminalSize(80, 24))
            }
            val tunnel = t as GatewayTunnel
            val g = GatewayClient(tunnel, "tok123")

            // ls: jail içi JSON liste
            val entries = g.ls("")
            assertTrue(entries.any { it.isDir && it.name == "docs" })

            // dosya okuma
            assertEquals("hello-gateway\n", g.readFile("docs/ok.md"))

            // yanlış token → 401
            val (status, _) = tunnel.gatewayGet("/ls/", "wrong-token", 4096)
            assertEquals(401, status)

            // traversal → client tarafı reddeder (host da 400 döner)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { g.readFile("../escape") }
            }

            // preview: loopback dev-server üzerinden (test sunucusu 8899'da)
            val preview = runCatching { g.preview("127.0.0.1", 8899, "/") }.getOrNull()
            if (preview != null) {
                assertTrue(preview.contains("pa-preview-marker"))
            }

            // SSRF: client tarafı loopback olmayanı reddeder
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { g.preview("169.254.169.254", 80, "/") }
            }

            t.close()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
