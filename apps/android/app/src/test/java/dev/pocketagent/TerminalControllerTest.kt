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

    private suspend fun await(timeoutMs: Long = 3000, cond: () -> Boolean) {
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
}
