// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.*
import org.junit.Assert.*
import org.junit.Test

class ConnectionTest {
    @Test fun validPasses() {
        val c = SavedConnection("h", "example.com", 22, "u", "keystore:alias1")
        assertTrue(c.validate().isEmpty())
    }
    @Test fun rejectsSecretsAndBadPort() {
        val bad = SavedConnection("", "h", 99999, "u", "-----BEGIN OPENSSH PRIVATE KEY-----")
        val errs = bad.validate()
        assertTrue(errs.contains("name"))
        assertTrue(errs.contains("port"))
        assertTrue(errs.any { it.startsWith("credentialRef") })
    }
    @Test fun approvalBindsAllFields() {
        val a = approvalBind("e", "d", "3", "approve", "exp", "n1")
        val b = approvalBind("e", "d", "3", "approve", "exp", "n2")
        assertNotEquals(a, b) // nonce matters
        assertTrue(a.contains("approve"))
    }
}
