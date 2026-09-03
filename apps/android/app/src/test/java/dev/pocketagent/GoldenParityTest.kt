// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.*
import org.junit.Assert.*
import org.junit.Test

// P01 parity: same golden expectations as Go TestGoldenOK/Rejected.
class GoldenParityTest {
    @Test fun messageCap() {
        assertTrue(validateEventSummary("codex", "APPROVAL_REQUIRED", "hi"))
        assertFalse(validateEventSummary("codex", "APPROVAL_REQUIRED", "x".repeat(257)))
        assertFalse(validateEventSummary("nope", "APPROVAL_REQUIRED", "hi"))
    }
    @Test fun noFallbackOnAuthOrHostKey() {
        val caps = HostCapabilities(ssh = true, mosh = true, et = true)
        val policy = TransportPolicy()
        assertNull(selectNext(TerminalTransport.MOSH, TransportFailure.AuthFailed, policy, caps))
        assertNull(selectNext(TerminalTransport.MOSH, TransportFailure.HostKeyChanged, policy, caps))
        assertEquals(TerminalTransport.ET, selectNext(TerminalTransport.MOSH, TransportFailure.Network("udp-blocked"), policy, caps))
    }
}
