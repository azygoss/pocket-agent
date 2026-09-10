// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.SshConfig
import dev.pocketagent.transport.TerminalTransport
import dev.pocketagent.transport.parseAddHostLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConfigTest {
    @Test fun parsesHostBlocks() {
        val text = """
            Host vps
              HostName 203.0.113.10
              User deploy
              Port 2222
              IdentityFile ~/.ssh/id_ed25519

            Host nas
              HostName nas.local
              User admin
        """.trimIndent()
        val out = SshConfig.parse(text)
        assertEquals(2, out.size)
        assertEquals("vps", out[0].name)
        assertEquals("203.0.113.10", out[0].host)
        assertEquals(2222, out[0].port)
        assertEquals("deploy", out[0].user)
        assertEquals("~/.ssh/id_ed25519", out[0].identityFile)
        assertEquals("nas.local", out[1].host)
        assertEquals(22, out[1].port)
    }

    @Test fun wildcardAndNegationSkipped() {
        val text = """
            Host *
              User default
            Host !blocked denied
              User x
            Host web?
              User y
        """.trimIndent()
        assertTrue(SshConfig.parse(text).isEmpty())
    }

    @Test fun missingHostNameFallsBackToAlias() {
        val out = SshConfig.parse("Host myalias\n  User u\n")
        assertEquals("myalias", out[0].host)
    }

    @Test fun toConnectionMapsAuthKind() {
        val withKey = SshConfig.parse("Host a\n IdentityFile ~/.ssh/k\n User u\n")[0]
        val noKey = SshConfig.parse("Host b\n User u\n")[0]
        assertEquals("ram:pem", SshConfig.toConnection(withKey).credentialRef)
        assertEquals("ram:password", SshConfig.toConnection(noKey).credentialRef)
        assertEquals(listOf(TerminalTransport.SSH), SshConfig.toConnection(noKey).transportOrder)
    }
}

class AddHostLinkTest {
    @Test fun parsesFullLink() {
        val c = parseAddHostLink("pocketagent://add?host=1.2.3.4&user=dev&port=2222&name=myvps")!!
        assertEquals("myvps", c.name)
        assertEquals("1.2.3.4", c.host)
        assertEquals(2222, c.port)
        assertEquals("dev", c.user)
    }

    @Test fun minimalLinkUsesDefaults() {
        val c = parseAddHostLink("pocketagent://add?host=example.com")!!
        assertEquals("example.com", c.name)
        assertEquals(22, c.port)
    }

    @Test fun urlEncodedValues() {
        val c = parseAddHostLink("pocketagent://add?host=h&name=my%20box")!!
        assertEquals("my box", c.name)
    }

    @Test fun rejectsWrongSchemeAndMissingHost() {
        assertNull(parseAddHostLink("pocketagent://tmux"))
        assertNull(parseAddHostLink("https://add?host=x"))
        assertNull(parseAddHostLink("pocketagent://add?user=u"))
    }
}
