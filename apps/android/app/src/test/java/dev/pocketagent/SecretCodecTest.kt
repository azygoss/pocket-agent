// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.security.SecretCodec
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.SessionId
import dev.pocketagent.transport.TerminalViewModel
import org.junit.Assert.*
import org.junit.Test

class SecretCodecTest {
    @Test fun passwordRoundtrip() {
        val s = Secret.Password("s3cr3t\u0001with-sep")
        assertEquals(s, SecretCodec.decode(SecretCodec.encode(s)))
    }
    @Test fun pemRoundtrip() {
        val pem = "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjE=\n-----END OPENSSH PRIVATE KEY-----"
        val s = Secret.PemKey(pem, "pp")
        assertEquals(s, SecretCodec.decode(SecretCodec.encode(s)))
        val noPass = Secret.PemKey(pem, null)
        assertEquals(noPass, SecretCodec.decode(SecretCodec.encode(noPass)))
    }
    @Test fun rejectsGarbage() {
        assertNull(SecretCodec.decode("".toByteArray()))
        assertNull(SecretCodec.decode("X".toByteArray()))
        assertNull(SecretCodec.decode("XY".toByteArray()))
    }
}

class TerminalHistoryTest {
    @Test fun pushDeduplicatesAndCaps() {
        val vm = TerminalViewModel(SessionId("s"))
        vm.pushHistory("ls")
        vm.pushHistory("pwd")
        vm.pushHistory("ls") // dup: en başa taşınır, kopya olmaz
        assertEquals(2, vm.historyCount)
        assertEquals("ls", vm.historyOlder())
        assertEquals("pwd", vm.historyOlder())
        assertNull(vm.historyOlder()) // en eskide kalır
        assertEquals("ls", vm.historyNewer())
        assertEquals("", vm.historyNewer()) // geçmişten çık
        assertEquals("", vm.historyNewer()) // idempotent
        vm.pushHistory("") // boş kayıt yok
        vm.pushHistory("\n")
        assertEquals(2, vm.historyCount)
        repeat(120) { vm.pushHistory("cmd-$it") }
        assertEquals(100, vm.historyCount)
    }
}
