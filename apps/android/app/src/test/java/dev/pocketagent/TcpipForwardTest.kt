// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.LocalForwarder
import dev.pocketagent.transport.TcpipCapable
import dev.pocketagent.transport.TcpipChannel
import dev.pocketagent.transport.parseListenPorts
import dev.pocketagent.transport.previewPortsFromTexts
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

    @Test fun previewPortsNewestFirst() {
        val texts = listOf(
            "kimi web listening at http://localhost:8080/",
            "dev server: http://127.0.0.1:9000",
            "duplicate http://localhost:8080",
        )
        assertEquals(listOf(8080, 9000), previewPortsFromTexts(texts))
        assertEquals(emptyList<Int>(), previewPortsFromTexts(listOf("https://example.com:443")))
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
            // Tünel hedefi her zaman host loopback'i.
            assertEquals(listOf("127.0.0.1" to echo.localPort), tcpip.targets)
        } finally {
            fwd.close()
            echo.close()
            echoJob.join()
        }
    }
}
