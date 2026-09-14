// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.LocalForwarder
import dev.pocketagent.transport.TcpipCapable
import dev.pocketagent.transport.TcpipChannel
import dev.pocketagent.transport.PreviewTarget
import dev.pocketagent.transport.localPath
import dev.pocketagent.transport.parseListenPorts
import dev.pocketagent.transport.parsePreviewProbe
import dev.pocketagent.transport.previewTargetsFromTexts
import dev.pocketagent.transport.previewTokenFromTexts
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

// openTcpip'i gerçek bir yerel sokete bağlayan fake — relay mantığını
// SSHJ olmadan uçtan uca sınar.
private class LoopbackTcpip : TcpipCapable {
    var targets = mutableListOf<Pair<String, Int>>()
    override suspend fun openTcpip(host: String, port: Int): TcpipChannel {
        targets.add(host to port)
        val s = Socket(host, port)
        return object : TcpipChannel {
            override val input: InputStream get() = s.getInputStream()
            override val output: OutputStream get() = s.getOutputStream()
            override fun close() { runCatching { s.close() } }
        }
    }
}

class TcpipForwardTest {

    @Test fun parseListenPortsSs() {
        val ss = """
            LISTEN 0      511          127.0.0.1:8080      0.0.0.0:*
            LISTEN 0      4096           0.0.0.0:22        0.0.0.0:*
            LISTEN 0      100              [::1]:3000         [::]:*
        """.trimIndent()
        assertEquals(listOf(22, 3000, 8080), parseListenPorts(ss))
    }

    @Test fun parseListenPortsNetstat() {
        val netstat = """
            Active Internet connections (only servers)
            Proto Recv-Q Send-Q Local Address           Foreign Address         State
            tcp        0      0 127.0.0.1:8080          0.0.0.0:*               LISTEN
            tcp6       0      0 :::3000                 :::*                    LISTEN
        """.trimIndent()
        assertEquals(listOf(3000, 8080), parseListenPorts(netstat))
    }

    @Test fun previewTargetsNewestFirst() {
        val texts = listOf(
            "kimi web listening at http://localhost:8080/",
            "dev server: http://127.0.0.1:9000",
            "duplicate http://localhost:8080",
        )
        // Port başına tek hedef; en yeni URL önce.
        assertEquals(
            listOf(
                PreviewTarget("localhost", 8080, "/"),
                PreviewTarget("127.0.0.1", 9000, "/"),
            ),
            previewTargetsFromTexts(texts),
        )
        assertEquals(emptyList<PreviewTarget>(), previewTargetsFromTexts(listOf("https://example.com:443")))
    }

    @Test fun previewTargetsPreservesPathAndQuery() {
        // kimi web bazı kurulumlarda token'lı path basar — yol korunmalı.
        val texts = listOf("open http://127.0.0.1:4321/ui?token=abc123 to start")
        assertEquals(
            listOf(PreviewTarget("127.0.0.1", 4321, "/ui?token=abc123")),
            previewTargetsFromTexts(texts),
        )
    }

    @Test fun previewTokenFromBannerLine() {
        // kimi web banner'ı: "Token:   <v>" — en yeni satır kazanır
        // (rotate-token sonrası eski değer scrollback'te kalır).
        val texts = listOf(
            "Local:   http://127.0.0.1:58627/#token=eski-deger",
            "Token:   eski-deger",
            "Stop:    Ctrl+C",
            "Token:   yeni-deger.123",
        )
        assertEquals("yeni-deger.123", previewTokenFromTexts(texts))
        assertEquals(null, previewTokenFromTexts(listOf("token yok burada")))
    }

    @Test fun localPathInjectsTokenFragment() {
        // Token yoksa path aynen; varsa #token= enjekte/değiştirilir.
        assertEquals("/", PreviewTarget("localhost", 1, "/").localPath(null))
        assertEquals("/#token=abc", PreviewTarget("localhost", 1, "/").localPath("abc"))
        // Wrap'te kırılmış fragment tam değerle değiştirilir.
        assertEquals("/#token=tam", PreviewTarget("x", 1, "/#token=kir").localPath("tam"))
        // SPA route fragment'ı korunur.
        assertEquals("/ui#/dash&token=t", PreviewTarget("x", 1, "/ui#/dash").localPath("t"))
        // Token yoksa var olan fragment olduğu gibi kalır.
        assertEquals("/#token=url", PreviewTarget("x", 1, "/#token=url").localPath(null))
    }

    @Test fun parsePreviewProbeSplitsPortsAndToken() {
        val out = """
            LISTEN 0      511          127.0.0.1:8080      0.0.0.0:*
            @@PA-TOK@@
            gizli-token-123
        """.trimIndent()
        val p = parsePreviewProbe(out)
        assertEquals(listOf(8080), p.ports)
        assertEquals("gizli-token-123", p.token)
        // Token dosyası yoksa marker sonrası boş → token null.
        assertEquals(null, parsePreviewProbe("LISTEN 0 1 127.0.0.1:22 0.0.0.0:*\n@@PA-TOK@@\n").token)
        // Marker hiç yoksa da port parse çalışır.
        assertEquals(listOf(22), parsePreviewProbe("LISTEN 0 1 127.0.0.1:22 0.0.0.0:*").ports)
    }

    @Test fun forwarderPrefersRemotePortLocally() = runBlocking {
        // Boş port → aynı portta dinler (Host/Origin uzak servisle eşleşir).
        val free = ServerSocket(0).use { it.localPort }
        val fwd = LocalForwarder(this, LoopbackTcpip(), free)
        try {
            assertEquals(free, fwd.start(free))
        } finally {
            fwd.close()
        }
    }

    @Test fun forwarderFallsBackWhenPreferredPortTaken() = runBlocking {
        // Port dolu → rastgele porta düşer, yine de dinler.
        val taken = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val fwd = LocalForwarder(this, LoopbackTcpip(), taken.localPort)
        try {
            val p = fwd.start(taken.localPort)
            org.junit.Assert.assertTrue(p in 1..65535 && p != taken.localPort)
        } finally {
            fwd.close()
            taken.close()
        }
    }

    @Test fun channelErrorIsSurfaced() = runBlocking {
        // openTcpip fırlatırsa lastChannelError'a yazılır (sshd
        // AllowTcpForwarding=no teşhisi sessiz kalmaz).
        val failing = object : TcpipCapable {
            override suspend fun openTcpip(host: String, port: Int): TcpipChannel =
                throw java.io.IOException("administratively prohibited")
        }
        val fwd = LocalForwarder(this, failing, 9999)
        val local = fwd.start()
        try {
            Socket("127.0.0.1", local).use { it.getOutputStream().write(1) }
            kotlinx.coroutines.withTimeout(3000) {
                while (fwd.lastChannelError == null) kotlinx.coroutines.delay(10)
            }
            assertEquals("administratively prohibited", fwd.lastChannelError)
        } finally {
            fwd.close()
        }
    }

    @Test fun forwarderRelaysBytes() = runBlocking {
        // Yerel echo server: gelen ilk baytları geri yazar.
        val echo = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val echoJob = launch(Dispatchers.IO) {
            val c = echo.accept()
            val buf = ByteArray(1024)
            val n = c.getInputStream().read(buf)
            if (n > 0) { c.getOutputStream().write(buf, 0, n); c.getOutputStream().flush() }
            c.close()
        }
        val tcpip = LoopbackTcpip()
        val fwd = LocalForwarder(this, tcpip, echo.localPort)
        val local = fwd.start()
        try {
            Socket("127.0.0.1", local).use { client ->
                client.getOutputStream().write("ping".toByteArray())
                client.getOutputStream().flush()
                val buf = ByteArray(4)
                var off = 0
                while (off < 4) {
                    val n = client.getInputStream().read(buf, off, 4 - off)
                    if (n < 0) break
                    off += n
                }
                assertEquals("ping", String(buf, 0, off))
            }
            // Tünel hedefi varsayılan olarak "localhost" — host tarafında
            // resolve edilir, ::1/127.0.0.1 bind farkını kapsar.
            assertEquals(listOf("localhost" to echo.localPort), tcpip.targets)
        } finally {
            fwd.close()
            echo.close()
            echoJob.join()
        }
    }
}
