// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.session.PersistedSession
import dev.pocketagent.session.SessionResume
import dev.pocketagent.transport.*
import org.junit.Assert.*
import org.junit.Test

class SshTransportTest {
    @Test fun ptySizeGuarded() {
        assertEquals(80, TerminalSize(80, 24).cols)
        try { TerminalSize(0, 24); fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun managerOrchestratesAndHardStops() {
        val m = TransportManager()
        assertEquals(TerminalTransport.MOSH, m.start())
        assertEquals(TerminalTransport.ET, m.onFailure(TransportFailure.Network("udp")))
        assertEquals(TerminalTransport.SSH, m.onFailure(TransportFailure.MissingServer("mosh")))
        assertNull(m.onFailure(TransportFailure.AuthFailed)) // terminal: no fallback
    }
    @Test fun resumeNeverReruns() {
        val last = PersistedSession("p1", "TMUX", "sess0")
        assertEquals(last, SessionResume.resumeTarget(last, true))
        assertNull(SessionResume.resumeTarget(last, false))
        assertNull(SessionResume.resumeTarget(null, true))
        assertNull(SessionResume.resumeTarget(PersistedSession("p1", "TMUX", ""), true))
    }
    @Test fun frameEquality() {
        assertEquals(TerminalFrame(byteArrayOf(1, 2), TerminalTransport.SSH), TerminalFrame(byteArrayOf(1, 2), TerminalTransport.SSH))
        assertNotEquals(TerminalFrame(byteArrayOf(1), TerminalTransport.SSH), TerminalFrame(byteArrayOf(1), TerminalTransport.ET))
    }
}
