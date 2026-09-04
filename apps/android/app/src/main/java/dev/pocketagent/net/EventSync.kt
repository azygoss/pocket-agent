// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.withContext

// P13: FCM gelene kadar polling fallback (planda izinli). Backend ayarlanmışsa
// 15s'de bir event özeti çeker; hata durumunda üstel geri çekilme (maks 5dk).
// syncNow() bekleyen turu anında uyandırır (UI "şimdi senkronla").
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EventSync(
    private val scope: CoroutineScope,
    private val clientFor: () -> BackendClient?,
    private val onEvents: (List<BackendEvent>) -> Unit,
    private val intervalMs: Long = 15_000,
    private val onUsages: ((org.json.JSONArray) -> Unit)? = null,
) {
    private var job: Job? = null
    private var cursor: String? = null
    // Manuel senkron: UI'daki "şimdi" düğmesi bekleyen turu uyandırır.
    private val tick = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    fun syncNow() { tick.trySend(Unit) }

    private val _status = MutableStateFlow("kapalı")
    val status: StateFlow<String> = _status

    fun start() {
        if (job != null) return
        job = scope.launch {
            var backoff = intervalMs
            while (true) {
                val client = clientFor()
                if (client == null) {
                    _status.value = "backend ayarlanmadı"
                    delay(intervalMs)
                    continue
                }
                try {
                    val events = withContext(Dispatchers.IO) { client.events(cursor) }
                    if (events.isNotEmpty()) {
                        cursor = events.last().eventId
                        onEvents(events)
                    }
                    onUsages?.let { cb ->
                        withContext(Dispatchers.IO) { runCatching { client.usages() }.getOrNull() }?.let(cb)
                    }
                    _status.value = "bağlı"
                    backoff = intervalMs
                } catch (e: Exception) {
                    _status.value = "hata: ${e.message ?: "bağlantı"}"
                    backoff = (backoff * 2).coerceAtMost(300_000)
                }
                kotlinx.coroutines.selects.select<Unit> {
                    onTimeout(backoff) {}
                    tick.onReceive {}
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = "kapalı"
    }
}
