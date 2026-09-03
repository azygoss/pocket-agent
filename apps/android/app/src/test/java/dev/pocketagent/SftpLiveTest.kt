// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SftpSession
import dev.pocketagent.transport.SshjConnector
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

// Canlı SFTP kanıtı: gerçek sshd üzerinden list/read/write. Yalnız env
// değişkenleriyle açılır (PA_LIVE_SSH=1 PA_LIVE_USER=… PA_LIVE_PEM=…).
class SftpLiveTest {
    @Test fun listReadWrite() {
        runBlocking {
        val pemPath = System.getenv("PA_LIVE_PEM")
        val user = System.getenv("PA_LIVE_USER") ?: "pa-dev"
        assumeTrue("live ssh yok", System.getenv("PA_LIVE_SSH") == "1" && pemPath != null)

        val store = TofuHostKeyStore(File.createTempFile("hostkeys", ".db"))
        val scope = CoroutineScope(Dispatchers.IO)
        val connector = SshjConnector(store, scope)
        val conn = SavedConnection("live", "127.0.0.1", 22, user, "ram:pem")
        val pem = File(pemPath!!).readText()

        // İlk denemede TOFU sunumu gelir → pinle → tekrar bağlan.
        val t = try {
            connector.open(conn, Secret.PemKey(pem), TerminalSize(80, 24))
        } catch (e: dev.pocketagent.transport.UnknownHostKeyException) {
            store.pin(e.presented)
            connector.open(conn, Secret.PemKey(pem), TerminalSize(80, 24))
        }
        val sftp = t as SftpSession

        val home = sftp.home()
        assertTrue(home.startsWith("/"))

        val root = sftp.list("/")
        assertTrue(root.any { it.isDir && it.name == "etc" })
        // dizinler önce sıralanır
        val firstFile = root.indexOfFirst { !it.isDir }
        val lastDir = root.indexOfLast { it.isDir }
        assertTrue(lastDir < firstFile || firstFile < 0)

        val marker = "pa-sftp-" + System.currentTimeMillis()
        val remote = "/tmp/$marker.txt"
        sftp.writeBytes(remote, "selam-$marker".toByteArray())
        val back = sftp.readBytes(remote, 4096).decodeToString()
        assertEquals("selam-$marker", back)

        // kota: maxBytes keser
        val cut = sftp.readBytes(remote, 5)
        assertEquals(5, cut.size)

        val listed = sftp.list("/tmp")
        assertTrue(listed.any { it.name == "$marker.txt" && it.size == back.toByteArray().size.toLong() })

        t.close()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
