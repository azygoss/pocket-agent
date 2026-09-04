// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.ExecCapable
import dev.pocketagent.transport.MoshBootstrap
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

class MoshBootstrapTest {
    @Test fun parseConnectLine() {
        val s = MoshBootstrap.parseConnect("mosh-server (mosh 1.4.0)\nMOSH CONNECT 60001 AbCdEfGhIjKlMnOpQrStUv\n")
        assertNotNull(s)
        assertEquals(60001, s!!.udpPort)
        assertEquals("AbCdEfGhIjKlMnOpQrStUv", s.key)
        assertNull(MoshBootstrap.parseConnect("garbage output"))
        assertNull(MoshBootstrap.parseConnect("MOSH CONNECT abc key")) // port sayı değil
    }

    // Canlı: VPS'te mosh-server kurulu olmalı (apt install mosh).
    @Test fun liveMoshServerBootstrap() {
        runBlocking {
            val pemPath = System.getenv("PA_LIVE_PEM")
            val user = System.getenv("PA_LIVE_USER") ?: "pa-dev"
            assumeTrue("live ssh yok", System.getenv("PA_LIVE_SSH") == "1" && pemPath != null)

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
            val exec = t as ExecCapable

            // exec kanalı çalışıyor mu
            val (code, out) = exec.exec("echo exec-ok")
            assertEquals(0, code)
            assertTrue(out.contains("exec-ok"))

            // mosh-server bootstrap: anahtar SSH içinde döner
            val session = MoshBootstrap.start(exec)
            assertNotNull("mosh-server host'ta kurulu olmalı (apt install mosh)", session)
            assertTrue(session!!.udpPort in 60000..61000)
            assertTrue(session.key.length >= 20)

            t.close()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
