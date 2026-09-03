// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.net.BackendClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Canlı backend doğrulaması — varsayılan kapalı. Önce backend'i başlat:
//   go build -o /tmp/pa-backend ./backend/cmd/server && /tmp/pa-backend &
// Sonra:
//   PA_LIVE_BACKEND=http://127.0.0.1:8080 \
//   ./gradlew :app:testDebugUnitTest --tests dev.pocketagent.BackendLiveTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackendLiveTest {
    @Test fun eventsAndApprovalCas() {
        val base = System.getenv("PA_LIVE_BACKEND") ?: ""
        assumeTrue("set PA_LIVE_BACKEND to run", base.isNotBlank())
        val tenant = "t-android-${System.currentTimeMillis()}"
        val c = BackendClient(base, tenant)
        assertTrue("healthz", c.health())

        // Host tarafı: whitelist'e uygun iki event gönder (curl eşdeğeri).
        val post = { id: String ->
            val body = """
                {"event_id":"$id","opaque_host_id":"h_test","opaque_session_id":"s_1",
                 "source":"codex","category":"APPROVAL_REQUIRED","title":"t","message":"deploy?",
                 "request_digest":"d-$id","revision":"1",
                 "created_at":"2026-09-03T12:00:00Z","expires_at":"2026-09-04T12:00:00Z"}
            """.trimIndent()
            val conn = java.net.URL("$base/v1/hosts/h_test/events").openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("X-Tenant", tenant)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode
        }
        assertEquals(202, post("event:live-a"))
        assertEquals(202, post("event:live-b"))

        // Cihaz tarafı: özetler çekilir; terminal içeriği asla taşınmaz.
        val events = c.events()
        assertEquals(2, events.size)
        assertEquals("deploy?", events[0].message)
        assertEquals("codex", events[0].source)

        // İmleç sonrası yalnız yeniler gelir.
        val fresh = c.events(after = "event:live-b")
        assertTrue(fresh.isEmpty())

        // CAS: ilk karar kazanır, ikinci 409.
        assertEquals(202, c.approvalAction("event:live-a", "d-event:live-a", "1", "dev-1"))
        assertEquals(409, c.approvalAction("event:live-a", "d-event:live-a", "1", "dev-2"))

        // Başka tenant bu olayları göremez (izolasyon).
        assertTrue(BackendClient(base, "t-baska").events().isEmpty())
    }
}
