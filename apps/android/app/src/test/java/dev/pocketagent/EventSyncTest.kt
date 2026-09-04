// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.net.BackendEvent
import dev.pocketagent.net.EventSync
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

// BackendClient final sınıf; EventSync'i gerçek client yerine null-client
// dışında test etmek için clientFor lambdasını kullanıyoruz.
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class EventSyncTest {
    @Test fun syncNowWakesPollingLoop() = runBlocking {
        // Backend yokken loop "backend ayarlanmadı" durumunda bekler; syncNow
        // durumu değiştirmez ama loop'un canlı kaldığını status üzerinden izleriz.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sync = EventSync(scope, { null }, onEvents = {}, intervalMs = 10_000)
        sync.start()
        withTimeout(2000) { while (sync.status.value != "backend ayarlanmadı") delay(10) }
        sync.syncNow() // bekleyen turu uyandırır — crash olmadan devam etmeli
        delay(200)
        assertEquals("backend ayarlanmadı", sync.status.value)
        sync.stop()
        assertEquals("kapalı", sync.status.value)
    }

    @Test fun parseHelpersUsed() {
        // BackendClient.parseEvents zaten BackendLiveTest'te canlı; burada
        // boş dizi kenarını sabitle.
        assertEquals(0, dev.pocketagent.net.BackendClient.parseEvents("[]").size)
    }
}
