// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.data.Backup
import dev.pocketagent.data.PersistedSettings
import dev.pocketagent.transport.SavedConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Yedekleme codec'i org.json kullanır — Robolectric gerçek implementasyonu sağlar.
@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private fun conn(name: String) = SavedConnection(
        name = name, host = "$name.example.com", port = 22, user = "u",
        credentialRef = "ram:password",
    )

    @Test fun roundTrip() {
        val json = Backup.export(
            listOf(conn("vps"), conn("nas").copy(autoTmux = true, port = 2200)),
            PersistedSettings(fontScale = 1.4f, backendUrl = "https://b", themeId = "nord", snippets = "gs=git status"),
        )
        val r = Backup.import(json)
        assertEquals(2, r.connections.size)
        assertEquals("vps", r.connections[0].name)
        assertTrue(r.connections[1].autoTmux)
        assertEquals(2200, r.connections[1].port)
        assertEquals(1.4f, r.settings!!.fontScale, 0.001f)
        assertEquals("gs=git status", r.settings!!.snippets)
        assertEquals(0, r.skipped)
    }

    @Test fun secretsNeverExported() {
        val json = Backup.export(listOf(conn("x")), PersistedSettings(tenantToken = "s3cret-token"))
        assertFalse(json.contains("s3cret-token"))
        // auth alanı "password" etiketi taşır ama secret DEĞERİ asla girmez
        assertFalse(json.contains("secret", ignoreCase = true))
    }

    @Test fun invalidEntriesSkipped() {
        val json = """{"version":1,"connections":[{"name":"ok","host":"h","port":22,"user":"u"},{"name":"","host":"","port":0,"user":""}]}"""
        val r = Backup.import(json)
        assertEquals(1, r.connections.size)
        assertEquals(1, r.skipped)
    }

    @Test fun unknownVersionRejected() {
        try {
            Backup.import("""{"version":99}""")
            error("should reject")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("sürüm"))
        }
    }
}
