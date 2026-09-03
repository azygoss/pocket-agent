// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.*
import org.junit.Assert.*
import org.junit.Test

// P08 6-scenario fallback matrix (headless, no emulator).
class TransportMatrixTest {
    private val full = HostCapabilities(ssh = true, mosh = true, et = true)
    private val policy = TransportPolicy()

    @Test fun moshOk_staysOnMosh() {
        assertEquals(TerminalTransport.MOSH, selectNext(null, TransportFailure.Network("init"), policy, full))
    }
    @Test fun udpBlocked_moshToEt() {
        assertEquals(TerminalTransport.ET, selectNext(TerminalTransport.MOSH, TransportFailure.Network("udp-blocked"), policy, full))
    }
    @Test fun noMoshServer_etToSsh() {
        assertEquals(TerminalTransport.SSH, selectNext(TerminalTransport.ET, TransportFailure.MissingServer("mosh-server"), policy, full))
    }
    @Test fun tcpBlocked_etToSsh() {
        assertEquals(TerminalTransport.SSH, selectNext(TerminalTransport.ET, TransportFailure.Network("tcp-blocked"), policy, full))
    }
    @Test fun sshOnly_staysSsh() {
        val caps = HostCapabilities(ssh = true)
        assertEquals(TerminalTransport.SSH, selectNext(null, TransportFailure.Network("init"), policy, caps))
        assertNull(selectNext(TerminalTransport.SSH, TransportFailure.Network("down"), policy, caps))
    }
    @Test fun authOrHostKey_neverFallback() {
        assertNull(selectNext(TerminalTransport.MOSH, TransportFailure.AuthFailed, policy, full))
        assertNull(selectNext(TerminalTransport.ET, TransportFailure.HostKeyChanged, policy, full))
    }
}

class LinksTest {
    @Test fun deepLinks() {
        assertEquals(SessionProvider.TMUX, parseDeepLink("pocketagent://tmux?x=1")?.provider)
        assertEquals(SessionProvider.HERDR, parseDeepLink("pocketagent://herdr")?.provider)
        assertNull(parseDeepLink("pocketagent://shell;rm -rf"))
        assertNull(parseDeepLink("https://evil/x"))
    }
    @Test fun jailParityWithGo() {
        assertFalse(jailOk("../../etc/passwd"))
        assertFalse(jailOk("/etc/passwd"))
        assertFalse(jailOk("a/../b.md"))
        assertTrue(jailOk("docs/a.md"))
    }
    @Test fun ssrfParityWithGo() {
        assertTrue(loopbackOk("127.0.0.1"))
        assertTrue(loopbackOk("::1"))
        assertFalse(loopbackOk("169.254.169.254"))
        assertFalse(loopbackOk("example.com"))
    }
}
