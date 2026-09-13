// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.net.UpdateChecker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class) // org.json Android stub'ı — Robolectric gerekli
class UpdateCheckerTest {
    @Test fun isNewerComparesSegments() {
        assertTrue(UpdateChecker.isNewer("v0.27.0", "0.26.0"))
        assertTrue(UpdateChecker.isNewer("0.26.1", "0.26.0"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.99.9"))
        assertFalse(UpdateChecker.isNewer("0.26.0", "0.26.0"))
        assertFalse(UpdateChecker.isNewer("0.25.9", "0.26.0"))
        assertFalse(UpdateChecker.isNewer("v0.26.0", "0.26.1"))
    }

    @Test fun isNewerHandlesSuffixes() {
        assertTrue(UpdateChecker.isNewer("v0.27.0-rc1", "0.26.9"))
        assertFalse(UpdateChecker.isNewer("0.26.0", "0.26.0+build5"))
    }

    @Test fun parseReleasePicksApkAsset() {
        val json = """
        {
          "tag_name": "v0.27.0",
          "body": "notlar\nikinci satır",
          "assets": [
            {"name": "checksums.txt", "browser_download_url": "https://x/sums", "size": 100},
            {"name": "pocket-agent-0.27.0-debug.apk", "browser_download_url": "https://x/app.apk", "size": 78000000}
          ]
        }
        """.trimIndent()
        val r = UpdateChecker.parseRelease(json)!!
        assertEquals("0.27.0", r.version)
        assertEquals("https://x/app.apk", r.apkUrl)
        assertEquals(78_000_000L, r.sizeBytes)
        assertTrue(r.notes.startsWith("notlar"))
    }

    @Test fun parseReleaseWithoutApkIsNull() {
        val json = """{"tag_name":"v0.27.0","assets":[{"name":"src.zip","browser_download_url":"https://x/z"}]}"""
        assertNull(UpdateChecker.parseRelease(json))
    }
}
