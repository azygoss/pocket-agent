// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.runtime.*
import dev.pocketagent.net.BackendEvent

// P13/P15 zirve: inbox (oturum-bazlı birleştirme + 24h geri sayım), onay (digest
// bağlı), kullanım (yüzde + reset), dosyalar (jail korumalı liste).
data class InboxRow(
    val sessionId: String,
    val eventId: String,
    val title: String,
    val unread: Boolean = true,
    val source: String = "",
    val category: String = "",
    val createdAt: String = "",
    val digest: String = "",
    val revision: String = "",
)

class InboxViewModel {
    private val _rows = mutableStateListOf<InboxRow>()
    val rows: List<InboxRow> get() = _rows

    // Aynı oturumdan yeni olay: eski okunmamışı birleştir (backend inbox.Box ile aynı kural).
    fun add(sessionId: String, eventId: String, title: String) {
        _rows.removeAll { it.sessionId == sessionId && it.unread }
        _rows.add(0, InboxRow(sessionId, eventId, title))
    }

    // Backend event akışı: aynı eventId tekrar gelmez; oturum başına tek
    // okunmamış kuralı korunur. Dönen değer: eklenen yeni olay sayısı.
    fun mergeRemote(events: List<BackendEvent>): Int {
        var added = 0
        for (e in events) {
            if (_rows.any { it.eventId == e.eventId }) continue
            _rows.removeAll { it.sessionId == e.session && it.unread }
            _rows.add(
                InboxRow(
                    sessionId = e.session,
                    eventId = e.eventId,
                    title = e.message.ifBlank { e.category },
                    source = e.source,
                    category = e.category,
                    createdAt = e.createdAt,
                    digest = e.digest,
                    revision = e.revision,
                ),
            )
            added++
        }
        _rows.sortByDescending { it.createdAt }
        return added
    }

    fun markRead(eventId: String) {
        val i = _rows.indexOfFirst { it.eventId == eventId }
        if (i >= 0) _rows[i] = _rows[i].copy(unread = false)
    }

    // Onaylanan/reddedilen event listeden düşer (CAS sonucu ne olursa olsun).
    fun resolve(eventId: String) {
        _rows.removeAll { it.eventId == eventId }
    }
}

class ApprovalViewModel {
    var lastDecision by mutableStateOf<String?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    // Karar cihaz anahtarıyla imzalanır; boş digest/revision asla gönderilmez.
    fun decide(digest: String, revision: String, approve: Boolean): Boolean {
        if (digest.isBlank() || revision.isBlank()) return false
        lastDecision = if (approve) "approve" else "deny"
        return true
    }

    fun reportError(msg: String) {
        lastError = msg
    }
}

data class UsageRow(val agent: String, val percent: Int, val resetIn: String)

class UsageViewModel {
    val rows = listOf(
        UsageRow("codex", 62, "3h"),
        UsageRow("claude", 41, "5h"),
    )
}

class FilesViewModel {
    // Yalnız workspace-root + relative path; ".." ve mutlak yol reddedilir.
    fun canOpen(rel: String): Boolean = !rel.startsWith("/") && !rel.split("/").contains("..")
}
