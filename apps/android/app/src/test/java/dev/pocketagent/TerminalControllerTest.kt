// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.AuthFailedException
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.FakeSshTransport
import dev.pocketagent.transport.PresentedKey
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SshConnector
import dev.pocketagent.transport.SshTransport
import dev.pocketagent.transport.TerminalController
import dev.pocketagent.transport.TerminalInput
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.transport.TransportFailure
import dev.pocketagent.transport.UnknownHostKeyException
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class TofuHostKeyStoreTest {
    private val key = PresentedKey("h", 22, "ssh-ed25519", byteArrayOf(1, 2, 3))

    @Test fun pinLookupForget() {
        val f = File.createTempFile("khst", null).apply { delete() }
        val store = TofuHostKeyStore(f)
        assertNull(store.lookup("h", 22))
        store.pin(key)
        assertTrue(store.lookup("h", 22)!!.sameKeyAs(key))
        assertTrue(key.fingerprint.startsWith("SHA256:"))
        // re-pin replaces
        val key2 = PresentedKey("h", 22, "ssh-ed25519", byteArrayOf(9, 9))
        store.pin(key2)
        assertTrue(store.lookup("h", 22)!!.sameKeyAs(key2))
        assertEquals(1, store.all().size)
        store.forget("h", 22)
        assertNull(store.lookup("h", 22))
        // reload from disk
        store.pin(key)
        assertTrue(TofuHostKeyStore(f).lookup("h", 22)!!.sameKeyAs(key))
    }
}

private class FakeConnector(
    private val store: TofuHostKeyStore,
    private val failAuth: Boolean = false,
) : SshConnector {
    override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport {
        if (failAuth) throw AuthFailedException()
        if (store.lookup(conn.host, conn.port) == null) {
            throw UnknownHostKeyException(PresentedKey(conn.host, conn.port, "ssh-ed25519", byteArrayOf(1, 2, 3)))
        }
        return FakeSshTransport().also { it.openPty("xterm-256color", size) }
    }
}

class TerminalControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val conn = SavedConnection("t", "h", 22, "u", "ram:password", id = "c1")

    private suspend fun await(timeoutMs: Long = 10_000, cond: () -> Boolean) {
        withTimeout(timeoutMs) { while (!cond()) delay(10) }
    }

    @Test fun tofuPromptThenConnectAndEcho() = runBlocking {
        val f = File.createTempFile("khst", null).apply { delete() }
        val store = TofuHostKeyStore(f)
        val c = TerminalController(scope, FakeConnector(store), store)

        c.connect(conn, Secret.Password("pw"))
        await { c.pendingHostKey.value != null }
        assertEquals(ConnectionState.CLOSED, c.state.value)
        assertEquals("h", c.pendingHostKey.value!!.host)

        c.acceptHostKeyAndReconnect()
        await { c.state.value == ConnectionState.ACTIVE }
        assertEquals("c1", c.connectedTo.value!!.id)
        assertNotNull(store.lookup("h", 22))

        c.send(TerminalInput.Text("whoami\n"))
        await { c.vm.frames.value.any { it.contains("> whoami") } }
        c.disconnect()
        assertEquals(ConnectionState.CLOSED, c.state.value)
        assertNull(c.connectedTo.value)
    }

    @Test fun authFailureHardStops() = runBlocking {
        val f = File.createTempFile("khst", null).apply { delete() }
        val store = TofuHostKeyStore(f)
        store.pin(PresentedKey("h", 22, "ssh-ed25519", byteArrayOf(1, 2, 3)))
        val c = TerminalController(scope, FakeConnector(store, failAuth = true), store)
        c.connect(conn, Secret.Password("bad"))
        await { c.state.value == ConnectionState.FAILED }
        assertEquals(TransportFailure.AuthFailed, c.failure.value)
    }

    @Test fun reconnectReusesLastProfile() = runBlocking {
        val f = File.createTempFile("khst", null).apply { delete() }
        val store = TofuHostKeyStore(f)
        store.pin(PresentedKey("h", 22, "ssh-ed25519", byteArrayOf(1, 2, 3)))
        val c = TerminalController(scope, FakeConnector(store), store)
        assertFalse(c.reconnect()) // hiç bağlanılmadı
        c.connect(conn, Secret.Password("pw"))
        await { c.state.value == ConnectionState.ACTIVE }
        c.disconnect()
        assertTrue(c.canReconnect())
        assertTrue(c.reconnect())
        await { c.state.value == ConnectionState.ACTIVE }
        assertEquals("c1", c.connectedTo.value!!.id)
        c.disconnect()
        assertFalse(c.canReconnect() && c.state.value == ConnectionState.ACTIVE)
    }
}

class AutoTmuxTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Test fun autoTmuxSendsAttachAfterConnect() = runBlocking {
        val f = File.createTempFile("khst", null).apply { delete() }
        val store = TofuHostKeyStore(f)
        val conn = SavedConnection("t", "h", 22, "u", "ram:password", id = "c1", autoTmux = true)
        // FakeConnector'a değil, doğrudan transport dönen bir connector gerek — basit inline:
        val transport = FakeSshTransport()
        val connector = object : SshConnector {
            override suspend fun open(c: SavedConnection, s: Secret?, size: TerminalSize): SshTransport {
                transport.openPty("xterm-256color", size)
                return transport
            }
        }
        val c = TerminalController(scope, connector, store)
        c.connect(conn, Secret.Password("pw"))
        withTimeout(5000) {
            while (transport.sent.none { it is TerminalInput.Text && it.s.contains("tmux new-session -A -s main") }) delay(20)
        }
        assertEquals(ConnectionState.ACTIVE, c.state.value)
        c.disconnect()
    }

    @Test fun noAutoTmuxByDefault() = runBlocking {
        val transport = FakeSshTransport()
        val connector = object : SshConnector {
            override suspend fun open(c: SavedConnection, s: Secret?, size: TerminalSize): SshTransport {
                transport.openPty("xterm-256color", size)
                return transport
            }
        }
        val store = TofuHostKeyStore(File.createTempFile("khst", null).apply { delete() })
        val c = TerminalController(scope, connector, store)
        c.connect(SavedConnection("t", "h", 22, "u", "ram:password", id = "c2"), Secret.Password("pw"))
        delay(900)
        assertTrue(transport.sent.none { it is TerminalInput.Text })
        c.disconnect()
    }

    // Hazır profiller: açılış komutu bağlanınca otomatik gönderilir.
    @Test fun startupCommandSendsAfterConnect() = runBlocking {
        val transport = FakeSshTransport()
        val connector = object : SshConnector {
            override suspend fun open(c: SavedConnection, s: Secret?, size: TerminalSize): SshTransport {
                transport.openPty("xterm-256color", size)
                return transport
            }
        }
        val store = TofuHostKeyStore(File.createTempFile("khst", null).apply { delete() })
        val c = TerminalController(scope, connector, store)
        c.connect(
            SavedConnection("t", "h", 22, "u", "ram:password", id = "c3"),
            Secret.Password("pw"),
            startupCommand = "codex",
        )
        withTimeout(5000) {
            while (transport.sent.none { it is TerminalInput.Text && it.s.contains("codex\n") }) delay(20)
        }
        c.disconnect()
    }

    // autoTmux + profil komutu: tmux önce — komut tmux oturumunun içine düşer.
    @Test fun startupCommandRunsInsideTmux() = runBlocking {
        val transport = FakeSshTransport()
        val connector = object : SshConnector {
            override suspend fun open(c: SavedConnection, s: Secret?, size: TerminalSize): SshTransport {
                transport.openPty("xterm-256color", size)
                return transport
            }
        }
        val store = TofuHostKeyStore(File.createTempFile("khst", null).apply { delete() })
        val c = TerminalController(scope, connector, store)
        c.connect(
            SavedConnection("t", "h", 22, "u", "ram:password", id = "c4", autoTmux = true),
            Secret.Password("pw"),
            startupCommand = "codex",
        )
        withTimeout(5000) {
            while (transport.sent.filterIsInstance<TerminalInput.Text>().size < 2) delay(20)
        }
        val texts = transport.sent.filterIsInstance<TerminalInput.Text>().map { it.s }
        assertTrue(texts[0].contains("tmux new-session -A -s main"))
        assertTrue(texts[1].contains("codex"))
        c.disconnect()
    }
}

// Kopmada otomatik yeniden bağlanma (0.9.x): ağ kopması/EOF → backoff retry,
// kullanıcı kapatması ve auth/host-key hatası → hard-stop (P08).
class AutoRetryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = TofuHostKeyStore(File.createTempFile("khrt", null).apply { delete() })
    private val conn = SavedConnection("t", "h", 22, "u", "ram:password", id = "c1")

    private fun pin() = store.pin(PresentedKey("h", 22, "ssh-ed25519", byteArrayOf(1, 2, 3)))

    private class RetryConnector(
        private val store: TofuHostKeyStore,
        private val failAuth: Boolean = false,
    ) : SshConnector {
        val opens = java.util.concurrent.atomic.AtomicInteger(0)
        val transports = mutableListOf<FakeSshTransport>()
        override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport {
            opens.incrementAndGet()
            if (failAuth) throw AuthFailedException()
            if (store.lookup(conn.host, conn.port) == null) {
                throw UnknownHostKeyException(PresentedKey(conn.host, conn.port, "ssh-ed25519", byteArrayOf(1, 2, 3)))
            }
            return FakeSshTransport().also { it.openPty("xterm-256color", size); transports.add(it) }
        }
    }

    private suspend fun awaitActive(c: TerminalController, timeoutMs: Long = 10_000) {
        withTimeout(timeoutMs) { while (c.state.value != ConnectionState.ACTIVE) delay(20) }
    }

    @Test fun remoteDropTriggersRetry() = runBlocking {
        pin()
        val fc = RetryConnector(store)
        val c = TerminalController(scope, fc, store)
        c.retryBaseMs = 50
        c.connect(conn, Secret.Password("pw"))
        awaitActive(c)
        assertEquals(1, fc.opens.get())
        // Uzaktan kopma: transport'u dışarıdan kapat (readLoop EOF)
        fc.transports.first().close()
        // önce ACTIVE'ten düşmesini bekle (yoksa ikinci awaitActive erken döner)
        withTimeout(10_000) { while (c.state.value == ConnectionState.ACTIVE) delay(20) }
        awaitActive(c) // retry yeniden bağlanır
        // retryJob'un rozeti sıfırlamasını bekle
        withTimeout(10_000) { while (c.retryAttempt.value != 0) delay(20) }
        assertTrue("en az 2 open beklenirdi", fc.opens.get() >= 2)
        c.disconnect()
    }

    @Test fun manualDisconnectDoesNotRetry() = runBlocking {
        pin()
        val fc = RetryConnector(store)
        val c = TerminalController(scope, fc, store)
        c.retryBaseMs = 50
        c.connect(conn, Secret.Password("pw"))
        awaitActive(c)
        c.disconnect()
        delay(400) // retry penceresinin çok ötesi
        assertEquals(1, fc.opens.get())
        assertEquals(ConnectionState.CLOSED, c.state.value)
    }

    @Test fun authFailureDuringRetryHardStops() = runBlocking {
        pin()
        // İlk open başarılı; kopma sonrası retry'daki open auth hatası versin
        val fc = object : SshConnector {
            val opens = java.util.concurrent.atomic.AtomicInteger(0)
            val transports = mutableListOf<FakeSshTransport>()
            override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport {
                val n = opens.incrementAndGet()
                if (n > 1) throw AuthFailedException()
                return FakeSshTransport().also { it.openPty("xterm-256color", size); transports.add(it) }
            }
        }
        val c = TerminalController(scope, fc, store)
        c.retryBaseMs = 50
        c.connect(conn, Secret.Password("pw"))
        awaitActive(c)
        fc.transports.first().close() // uzaktan kopma → retry başlar
        withTimeout(5_000) { while (c.state.value != ConnectionState.FAILED) delay(20) }
        withTimeout(5_000) { while (c.retryAttempt.value != 0) delay(20) } // retryJob yerleşsin
        assertEquals(TransportFailure.AuthFailed, c.failure.value)
        assertEquals(2, fc.opens.get()) // 1 bağlantı + 1 retry denemesi; hard-stop sonrası durdu
        delay(300)
        assertEquals("hard-stop: ek deneme olmamalı", 2, fc.opens.get())
        c.disconnect()
    }
}
