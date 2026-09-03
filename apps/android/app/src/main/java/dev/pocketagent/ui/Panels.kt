// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.runtime.*
import dev.pocketagent.net.BackendEvent
import kotlinx.coroutines.launch

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
    private val _rows = androidx.compose.runtime.mutableStateListOf<UsageRow>()
    val rows: List<UsageRow> get() = _rows

    // Backend /v1/usages (P13: türetilmiş snapshot, transcript asla). Şema
    // henüz sabit değil: snake_case ve Go field adlarını hoşgörülü karşıla.
    fun updateFrom(arr: org.json.JSONArray) {
        val parsed = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val agent = o.optString("agent").ifBlank { o.optString("Agent") }
            if (agent.isBlank()) return@mapNotNull null
            val pct = o.optInt("percent", o.optInt("Percent", -1))
            if (pct < 0) return@mapNotNull null
            val reset = o.optString("reset_in").ifBlank { o.optString("ResetIn", "") }
            UsageRow(agent, pct.coerceIn(0, 100), reset)
        }
        _rows.clear()
        _rows.addAll(parsed)
    }
}

class FilesViewModel(
    private val manager: dev.pocketagent.transport.SessionManager,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val cacheDir: java.io.File,
) {
    var path by mutableStateOf<String?>(null)
        private set
    val entries = androidx.compose.runtime.mutableStateListOf<dev.pocketagent.transport.RemoteFile>()
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    // name → içerik (küçük metin dosyaları için önizleme diyaloğu)
    var preview by mutableStateOf<Pair<String, String>?>(null)
        private set
    var downloading by mutableStateOf(false)
        private set
    // İndirilen dosya (paylaşım intent'i ekranda tetiklenir)
    var downloaded by mutableStateOf<java.io.File?>(null)

    fun hasActiveSftp(): Boolean = sftp() != null

    private fun sftp(): dev.pocketagent.transport.SftpSession? =
        manager.active()?.takeIf { it.state.value == dev.pocketagent.transport.ConnectionState.ACTIVE }?.sftp()

    // Oturum açıldığında/aktif değiştiğinde çağrılır.
    fun open() {
        if (path != null || loading) return
        val s = sftp() ?: return
        scope.launch {
            loading = true; error = null
            try {
                load(s.home())
            } catch (e: Exception) {
                error = e.message ?: "SFTP açılamadı"
                loading = false
            }
        }
    }

    fun refresh() {
        val p = path ?: return open()
        load(p)
    }

    fun cd(dir: String) = load(dir)

    fun up() {
        val p = path ?: return
        if (p == "/") return
        load(p.substringBeforeLast('/').ifEmpty { "/" })
    }

    private fun load(p: String) {
        val s = sftp() ?: run { error = "Aktif SSH oturumu yok"; return }
        scope.launch {
            loading = true; error = null
            try {
                val list = s.list(p)
                entries.clear(); entries.addAll(list)
                path = p
            } catch (e: Exception) {
                error = e.message ?: "Liste okunamadı"
            } finally {
                loading = false
            }
        }
    }

    // Dosyaya dokunma: küçükse önizle, değilse indir.
    fun onFile(f: dev.pocketagent.transport.RemoteFile) {
        if (f.isDir) { cd(f.path); return }
        if (f.size <= 64 * 1024) loadPreview(f) else download(f)
    }

    fun dismissPreview() { preview = null }
    fun clearDownloaded() { downloaded = null }

    private fun loadPreview(f: dev.pocketagent.transport.RemoteFile) {
        val s = sftp() ?: return
        scope.launch {
            loading = true; error = null
            try {
                val bytes = s.readBytes(f.path, 64 * 1024)
                preview = f.name to bytes.decodeToString()
            } catch (e: Exception) {
                error = e.message ?: "Okunamadı"
            } finally {
                loading = false
            }
        }
    }

    fun download(f: dev.pocketagent.transport.RemoteFile) {
        val s = sftp() ?: return
        scope.launch {
            downloading = true; error = null
            try {
                val bytes = s.readBytes(f.path, 10 * 1024 * 1024) // P15: 10MB cap
                val dir = java.io.File(cacheDir, "shared").apply { mkdirs() }
                val out = java.io.File(dir, f.name.ifBlank { "download" })
                out.writeBytes(bytes)
                downloaded = out
            } catch (e: Exception) {
                error = e.message ?: "İndirilemedi"
            } finally {
                downloading = false
            }
        }
    }

    fun upload(name: String, data: ByteArray) {
        val p = path ?: return
        val s = sftp() ?: return
        scope.launch {
            loading = true; error = null
            try {
                val target = if (p.endsWith("/")) p + name else "$p/$name"
                s.writeBytes(target, data)
                load(p)
            } catch (e: Exception) {
                error = e.message ?: "Yüklenemedi"
                loading = false
            }
        }
    }
}
