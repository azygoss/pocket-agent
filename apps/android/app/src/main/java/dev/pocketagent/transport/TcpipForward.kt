// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Host loopback'indeki web yayınlarını (kimi web, dev server, agent web-TUI)
// telefona taşıyan SSH-içi port forward. Telefonda 127.0.0.1:<yerel> dinlenir;
// her gelen bağlantı host'un 127.0.0.1:<uzak>'ına direct-tcpip kanalıyla
// bağlanır — ayrı auth yok, trafik mevcut SSH oturumunun içinde akar.
// Hedef yalnız loopback'e kilitli: tünel keyfi SSRF köprüsüne dönüşmez.

interface TcpipChannel {
    val input: InputStream
    val output: OutputStream
    fun close()
}

interface TcpipCapable {
    suspend fun openTcpip(host: String, port: Int): TcpipChannel
}

class LocalForwarder(
    private val scope: CoroutineScope,
    private val tcpip: TcpipCapable,
    private val remotePort: Int,
    private val remoteHost: String = "127.0.0.1",
) {
    private var server: ServerSocket? = null
    // Açık relay soketleri: forwarder kapanırken bloklu read'leri kırmak için
    // hepsi kapatılır (coroutine cancel blok I/O'yu kesmez).
    private val socks = java.util.Collections.synchronizedSet(mutableSetOf<Socket>())
    val localPort: Int get() = server?.localPort ?: 0

    fun start(): Int {
        require(loopbackOk(remoteHost) && remotePort in 1..65535) { "bad tunnel target" }
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 16)
        server = s
        scope.launch(Dispatchers.IO) {
            while (true) {
                val sock = try { s.accept() } catch (_: Exception) { break }
                launch { relay(sock) }
            }
        }
        return s.localPort
    }

    private suspend fun relay(sock: Socket) {
        socks.add(sock)
        val ch = try {
            tcpip.openTcpip(remoteHost, remotePort)
        } catch (_: Exception) {
            closeBoth(sock, null)
            return
        }
        try {
            coroutineScope {
                launch { pipe(sock.getInputStream(), ch.output) { closeBoth(sock, ch) } }
                launch { pipe(ch.input, sock.getOutputStream()) { closeBoth(sock, ch) } }
            }
        } finally {
            closeBoth(sock, ch)
        }
    }

    // Bir yön EOF/hata verince iki ucu da kapat — aksi halde diğer yönün
    // bloklu read'i sonsuza asılı kalır.
    private fun closeBoth(sock: Socket, ch: TcpipChannel?) {
        runCatching { ch?.close() }
        runCatching { sock.close() }
        socks.remove(sock)
    }

    private fun pipe(src: InputStream, dst: OutputStream, onDone: () -> Unit) {
        try {
            val buf = ByteArray(32768)
            while (true) {
                val n = src.read(buf)
                if (n < 0) break
                dst.write(buf, 0, n)
                dst.flush()
            }
        } catch (_: Exception) {
        } finally {
            onDone()
        }
    }

    fun close() {
        runCatching { server?.close() }
        synchronized(socks) { socks.toList() }.forEach { runCatching { it.close() } }
    }
}

// `ss -tlnH` ya da `netstat -tln` çıktısından dinlenen TCP portları —
// iki araçta da yerel adres 4. alan (0-tabanlı 3), veri satırı LISTEN içerir.
fun parseListenPorts(out: String): List<Int> =
    out.lineSequence()
        .filter { it.contains("LISTEN") }
        .mapNotNull { l ->
            val local = l.trim().split(Regex("\\s+")).getOrNull(3) ?: return@mapNotNull null
            local.substringAfterLast(':').toIntOrNull()?.takeIf { it in 1..65535 }
        }
        .distinct()
        .sorted()
        .toList()

// Terminal scrollback'inde görünen loopback URL'lerinden port adayları —
// kimi web gibi araçlar "http://localhost:PORT" satırını basar.
private val previewUrlRe =
    Regex("""https?://(?:localhost|127(?:\.\d{1,3}){3}|0\.0\.0\.0|\[?::1\]?)(?::(\d{1,5}))?""")

fun previewPortsFromTexts(texts: List<String>): List<Int> =
    texts.asSequence()
        .flatMap { previewUrlRe.findAll(it) }
        .mapNotNull { it.groupValues[1].toIntOrNull()?.takeIf { p -> p in 1..65535 } }
        .toList()
        .asReversed() // en son basılan URL en üstte
        .distinct()
