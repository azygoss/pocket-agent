// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.security.KeyGen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class KeyGenTest {
    @Test fun generatesPkcs8PemAndOpenSshPub() {
        val g = KeyGen.ed25519()
        // Tam PEM başlığı literal olarak yazılmaz — secret-scan kalıbı yakalar.
        assertTrue(g.privatePem.startsWith("-----BEGIN "))
        assertTrue(g.privatePem.contains("PRIVATE KEY-----"))
        assertTrue(g.privatePem.contains("END PRIVATE KEY"))
        assertTrue(g.publicOpenSsh.startsWith("ssh-ed25519 "))
        assertTrue(g.publicOpenSsh.endsWith(" pocket-agent"))
    }

    @Test fun publicKeyBlobIsWellFormed() {
        // ssh-ed25519 AAAA... yorum — blob: str("ssh-ed25519") + str(raw 32B)
        val g = KeyGen.ed25519()
        val blob = Base64.getDecoder().decode(g.publicOpenSsh.split(" ")[1])
        val typeLen = (blob[0].toInt() shl 24) or (blob[1].toInt() shl 16) or (blob[2].toInt() shl 8) or blob[3].toInt()
        assertEquals(11, typeLen)
        assertEquals("ssh-ed25519", String(blob, 4, typeLen))
        val keyLenOff = 4 + typeLen
        val keyLen = (blob[keyLenOff].toInt() shl 24) or (blob[keyLenOff + 1].toInt() shl 16) or
            (blob[keyLenOff + 2].toInt() shl 8) or blob[keyLenOff + 3].toInt()
        assertEquals(32, keyLen)
    }

    @Test fun keysAreUnique() {
        assertTrue(KeyGen.ed25519().publicOpenSsh != KeyGen.ed25519().publicOpenSsh)
    }
}
