// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.runtime.*
import dev.pocketagent.data.AgentEventDao
import dev.pocketagent.data.AgentEventEntity
import dev.pocketagent.net.BackendEvent
import kotlinx.coroutines.CoroutineScope
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
    // opaque_host_id ("h:<hash>") — bağlantı eşleşmesi için; kalıcı değil.
    val host: String = "",
)

// Aktif agent oturumları: sessionId başına son olay SESSION_STARTED ise aktif.
// (ended olayı aynı sessionId ile gelir; hook'lu agent'larda da aynı kural.)
fun activeSessions(rows: List<InboxRow>): List<InboxRow> =
    rows.groupBy { it.sessionId }
        .mapNotNull { (_, rs) -> rs.maxByOrNull { it.createdAt } }
        .filter { it.category == "SESSION_STARTED" }

// Inbox: in-memory satırlar + isteğe bağlı Room kalıcılığı (dao/scope verilirse).
// Uygulama yeniden başlasa bile 24s TTL içindeki olaylar geri yüklenir;
// resolve/markRead veritabanına da yansır.
class InboxViewModel(
    private val dao: AgentEventDao? = null,
    private val scope: CoroutineScope? = null,
) {
    // Kalıcı yazma tamamlanma sayacı: testler DB'yi yoklamak yerine bunu bekler
    // (Room in-memory tek bağlantı — eşzamanlı SELECT yoklaması yazıcıyı aç bırakır).
    private val _persistVersion = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val persistVersion: kotlinx.coroutines.flow.StateFlow<Long> = _persistVersion
    @Volatile var persistError: String? = null
        private set
    private val _rows = mutableStateListOf<InboxRow>()
    val rows: List<InboxRow> get() = _rows
    // resolve edilen eventId'ler: init yüklemesi geç tamamlanırsa silinmiş
    // olayın dirilmesini önler (init race koruması).
    private val resolvedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        val d = dao; val sc = scope
        if (d != null && sc != null) {
            sc.launch {
                val now = System.currentTimeMillis()
                d.sweepExpired(now)
                val persisted = d.active(now).map { e ->
                    InboxRow(
                        sessionId = e.sessionId, eventId = e.eventId, title = e.title,
                        unread = e.unread, source = e.source, category = e.category,
                        createdAt = java.time.Instant.ofEpochMilli(e.createdAt).toString(),
                        digest = e.digest, revision = e.revision,
                    )
                }
                for (row in persisted) {
                    if (row.eventId in resolvedIds) continue
                    if (_rows.none { it.eventId == row.eventId }) _rows.add(row)
                }
            }
        }
    }

    private fun persist(row: InboxRow) {
        val d = dao ?: return; val sc = scope ?: return
        val createdMs = runCatching { java.time.Instant.parse(row.createdAt).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
        sc.launch {
            runCatching {
            d.insert(
                AgentEventEntity(
                    eventId = row.eventId, sessionId = row.sessionId, source = row.source,
                    category = row.category, title = row.title, createdAt = createdMs,
                    expiresAt = createdMs + 24 * 3600_000L,
                    digest = row.digest, revision = row.revision, unread = row.unread,
                ),
            )
            }.onFailure { persistError = it.message ?: it.javaClass.simpleName }
            _persistVersion.value++
        }
    }

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
                    host = e.host,
                ),
            )
            persist(_rows.first { it.eventId == e.eventId })
            added++
        }
        _rows.sortByDescending { it.createdAt }
        return added
    }

    fun markRead(eventId: String) {
        val i = _rows.indexOfFirst { it.eventId == eventId }
        if (i >= 0) _rows[i] = _rows[i].copy(unread = false)
        val d = dao; val sc = scope
        if (d != null && sc != null) sc.launch { d.markRead(eventId); _persistVersion.value++ }
    }

    // Onaylanan/reddedilen event listeden düşer (CAS sonucu ne olursa olsun).
    fun resolve(eventId: String) {
        resolvedIds.add(eventId)
        _rows.removeAll { it.eventId == eventId }
        val d = dao; val sc = scope
        if (d != null && sc != null) sc.launch { d.deleteById(eventId); _persistVersion.value++ }
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

enum class FilesMode { SFTP, WORKSPACE }

// Önizleme koruması: ilk 8KB'da NUL varsa metin diyaloğuna binary basılmaz.
internal fun looksBinary(bytes: ByteArray): Boolean =
    bytes.take(8192).any { it == 0.toByte() }

// grep -n çıktısı: "yol:satırNo:metin" (yol içinde ':' olabilir — sağdan
// ilk geçerli sayısal alan satır numarasıdır).
data class GrepHit(val path: String, val line: Int, val text: String)

private val GREP_LINE = Regex("^(.+):(\\d+):(.*)$")

internal fun parseGrep(out: String): List<GrepHit> =
    out.lines().mapNotNull { l ->
        GREP_LINE.matchEntire(l)?.let {
            GrepHit(it.groupValues[1], it.groupValues[2].toInt(), it.groupValues[3])
        }
    }

class FilesViewModel(
    private val manager: dev.pocketagent.transport.SessionManager,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val cacheDir: java.io.File,
) {
    var mode by mutableStateOf(FilesMode.SFTP)
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
    // İndirilen dosyalar (tek öğe → SEND, çok öğe → SEND_MULTIPLE intent'i)
    var downloaded by mutableStateOf<List<java.io.File>?>(null)

    // P11 workspace (gateway tüneli): null = henüz sondalanmadı
    var gatewayAvailable by mutableStateOf<Boolean?>(null)
        private set
    var gwPath by mutableStateOf("")
        private set // workspace-göreli, "/"sız
    private var gatewayClient: dev.pocketagent.net.GatewayClient? = null

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
        if (mode == FilesMode.WORKSPACE) { gwRefresh(); return }
        val p = path ?: return open()
        load(p)
    }

    fun cd(dir: String) {
        if (mode == FilesMode.WORKSPACE) { gwLoad(dir); return }
        load(dir)
    }

    fun up() {
        if (mode == FilesMode.WORKSPACE) {
            if (gwPath.isEmpty()) return
            gwLoad(gwPath.substringBeforeLast('/', ""))
            return
        }
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
        if (mode == FilesMode.WORKSPACE) {
            // Agent transcript'i (JSONL) → sohbet görünümü (P14)
            if (f.name.endsWith(".jsonl")) { gwChat(f); return }
            gwPreview(f); return
        }
        if (f.size <= 64 * 1024) loadPreview(f) else download(f)
    }

    // Sohbet görünümü için bloklar (null = kapalı)
    var chatBlocks by mutableStateOf<Pair<String, List<dev.pocketagent.net.ChatBlockDto>>?>(null)
        private set
    fun dismissChat() { chatBlocks = null }

    private fun gwChat(f: dev.pocketagent.transport.RemoteFile) {
        val c = gatewayClient ?: return
        scope.launch {
            loading = true; error = null
            try {
                val blocks = c.chat(f.path)
                if (blocks.isEmpty()) {
                    // JSONL ama agent transcript'i değil — düz önizlemeye düş
                    gwPreview(f)
                } else {
                    chatBlocks = f.name to blocks
                }
            } catch (e: Exception) {
                // parse edilemeyen JSONL düz metin olarak gösterilir
                gwPreview(f)
            } finally {
                loading = false
            }
        }
    }

    fun dismissPreview() { preview = null }
    fun clearDownloaded() { downloaded = null }

    private fun loadPreview(f: dev.pocketagent.transport.RemoteFile) {
        val s = sftp() ?: return
        scope.launch {
            loading = true; error = null
            try {
                val bytes = s.readBytes(f.path, 64 * 1024)
                preview = f.name to
                    if (looksBinary(bytes)) "(ikili dosya — önizleme yok)"
                    else bytes.decodeToString()
            } catch (e: Exception) {
                error = e.message ?: "Okunamadı"
            } finally {
                loading = false
            }
        }
    }

    fun download(f: dev.pocketagent.transport.RemoteFile) = downloadAll(listOf(f))

    // Çoklu seçim indirmesi: dizinler atlanır; her dosya 10MB cap'li.
    // Aynı isim çakışmasında sonek eklenir.
    fun downloadAll(fs: List<dev.pocketagent.transport.RemoteFile>) {
        val s = sftp() ?: return
        scope.launch {
            downloading = true; error = null
            try {
                val dir = java.io.File(cacheDir, "shared").apply { mkdirs() }
                val used = mutableSetOf<String>()
                val out = fs.filter { !it.isDir }.map { f ->
                    val bytes = s.readBytes(f.path, 10 * 1024 * 1024) // P15: 10MB cap
                    var name = f.name.ifBlank { "download" }
                    while (!used.add(name)) name = "$name.${used.size}"
                    java.io.File(dir, name).apply { writeBytes(bytes) }
                }
                if (out.isEmpty()) {
                    error = "seçimde indirilecek dosya yok"
                } else {
                    downloaded = out
                }
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

    // Uzun-basma aksiyonları (SFTP modu; workspace gateway salt-okunur).
    fun delete(f: dev.pocketagent.transport.RemoteFile) = deleteAll(listOf(f))

    // Çoklu seçim silme: her öğe kendi onay akışından geçmiş varsayılır
    // (ekrandaki tek toplu onay); hata ilk kırılan öğede durur.
    fun deleteAll(fs: List<dev.pocketagent.transport.RemoteFile>) {
        val p = path ?: return
        val s = sftp() ?: return
        scope.launch {
            loading = true; error = null
            try {
                fs.forEach { s.delete(it.path, it.isDir) }
                load(p)
            } catch (e: Exception) {
                error = e.message ?: "Silinemedi"
                loading = false
            }
        }
    }

    // İçerik arama (grep): exec kanalından cwd'de recursive, .git hariç,
    // binary atlanır, dosya başına 3 + toplam 300 satır tavan.
    var grepResults by mutableStateOf<List<GrepHit>?>(null)
        private set
    var grepRunning by mutableStateOf(false)
        private set
    fun dismissGrep() { grepResults = null }

    fun grep(pattern: String) {
        val p = path ?: return
        val pat = pattern.trim()
        if (pat.isEmpty()) return
        val e = manager.active()?.exec() ?: run { error = "exec kanalı yok"; return }
        scope.launch {
            grepRunning = true; error = null
            try {
                val (_, out) = e.exec(
                    "grep -rInI --exclude-dir=.git -m 3 -- " +
                        dev.pocketagent.transport.shellQuote(pat) + " " +
                        dev.pocketagent.transport.shellQuote(p) + " | head -300",
                    15_000,
                )
                grepResults = parseGrep(out)
            } catch (e2: Exception) {
                error = e2.message ?: "arama başarısız"
            } finally {
                grepRunning = false
            }
        }
    }

    // Grep sonucuna git: üst dizine in + dosyayı önizle (64KB cap'li).
    fun revealPath(p: String) {
        val name = p.substringAfterLast('/')
        if (name.isEmpty()) return
        load(p.substringBeforeLast('/').ifEmpty { "/" })
        loadPreview(dev.pocketagent.transport.RemoteFile(name, p, false, 0, 0))
    }

    fun rename(f: dev.pocketagent.transport.RemoteFile, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean == f.name || clean.contains('/')) return
        val p = path ?: return
        val s = sftp() ?: return
        scope.launch {
            loading = true; error = null
            try {
                s.rename(f.path, if (p.endsWith("/")) p + clean else "$p/$clean")
                load(p)
            } catch (e: Exception) {
                error = e.message ?: "Ad değiştirilemedi"
                loading = false
            }
        }
    }

    // inside verilirse o dizinin içinde oluşturulur ve içine girilir;
    // yoksa cwd'de oluşturulur.
    fun mkdir(name: String, inside: dev.pocketagent.transport.RemoteFile? = null) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) return
        val parent = inside?.path ?: path ?: return
        val s = sftp() ?: return
        scope.launch {
            loading = true; error = null
            try {
                s.mkdir(if (parent.endsWith("/")) parent + clean else "$parent/$clean")
                load(parent)
            } catch (e: Exception) {
                error = e.message ?: "Klasör oluşturulamadı"
                loading = false
            }
        }
    }

    // Uzak yolu aktif PTY'ye shell-quote'lu yazar — cd/vim/agent input'una
    // doğrudan düşer. Kullanıcı Enter'a kendisi basar (komut çalıştırılmaz).
    fun pastePathToTerminal(f: dev.pocketagent.transport.RemoteFile): Boolean {
        val c = manager.active() ?: return false
        c.send(dev.pocketagent.transport.TerminalInput.Text(
            dev.pocketagent.transport.shellQuote(f.path),
        ))
        return true
    }

    // ---- P11 workspace (gateway) ----

    fun selectMode(m: FilesMode) {
        if (m == mode) return
        mode = m
        error = null
        if (m == FilesMode.WORKSPACE) {
            if (gatewayAvailable == null) probeGateway() else if (gatewayAvailable == true) gwRefresh()
        } else if (path != null) {
            refresh()
        }
    }

    // Gateway token'ı host'un 0600 dosyasından SFTP ile okunur (SSH oturumu
    // zaten doğrulanmış; ayrı kimlik yok). Token RAM'de kalır.
    fun probeGateway() {
        val s = sftp()
        val tunnel = manager.active()?.gateway()
        if (s == null || tunnel == null) { gatewayAvailable = false; return }
        scope.launch {
            loading = true; error = null
            try {
                val tokenPath = s.home().trimEnd('/') + "/.config/pocket-agent/gateway.token"
                val token = s.readBytes(tokenPath, 256).decodeToString().trim()
                if (token.isEmpty()) throw IllegalStateException("gateway token boş")
                val client = dev.pocketagent.net.GatewayClient(tunnel, token)
                val probe = client.ls("") // 200 gelmezse exception
                gatewayClient = client
                gatewayAvailable = true
                gwEntries(probe)
                gwPath = ""
            } catch (e: Exception) {
                gatewayAvailable = false
                gatewayClient = null
                error = null // gateway kurulu değil — sessizce SFTP modunda kal
            } finally {
                loading = false
            }
        }
    }

    fun gwRefresh() {
        val c = gatewayClient ?: return probeGateway()
        scope.launch {
            loading = true; error = null
            try {
                gwEntries(c.ls(gwPath))
            } catch (e: Exception) {
                error = e.message ?: "workspace listesi okunamadı"
            } finally {
                loading = false
            }
        }
    }

    private fun gwLoad(rel: String) {
        val c = gatewayClient ?: return probeGateway()
        scope.launch {
            loading = true; error = null
            try {
                gwEntries(c.ls(rel))
                gwPath = rel
            } catch (e: Exception) {
                error = e.message ?: "dizin okunamadı"
            } finally {
                loading = false
            }
        }
    }

    private fun gwEntries(list: List<dev.pocketagent.net.GatewayEntry>) {
        entries.clear()
        entries.addAll(
            list.map {
                dev.pocketagent.transport.RemoteFile(
                    name = it.name,
                    path = if (gwPath.isEmpty()) it.name else "$gwPath/${it.name}",
                    isDir = it.isDir,
                    size = it.size,
                    mtime = it.mtime,
                )
            },
        )
    }

    private fun gwPreview(f: dev.pocketagent.transport.RemoteFile) {
        val c = gatewayClient ?: return
        scope.launch {
            loading = true; error = null
            try {
                preview = f.name to c.readFile(f.path)
            } catch (e: Exception) {
                error = e.message ?: "okunamadı"
            } finally {
                loading = false
            }
        }
    }

    // Git diff görünümü (staged/unstaged/working/last) — önizleme diyaloğuna düşer.
    fun gwDiff(kind: String, label: String) {
        val c = gatewayClient ?: return
        scope.launch {
            loading = true; error = null
            try {
                val d = c.diff(kind)
                preview = label to d.ifBlank { "(değişiklik yok)" }
            } catch (e: Exception) {
                error = "diff alınamadı (workspace git repo mu?)"
            } finally {
                loading = false
            }
        }
    }

    // P14: allowlist dizinlerindeki son agent transcript'leri (workspace modunda listelenir)
    var transcripts by mutableStateOf<List<dev.pocketagent.net.TranscriptEntry>?>(null)
        private set

    fun loadTranscripts() {
        val c = gatewayClient ?: return
        scope.launch {
            transcripts = runCatching { c.chatRecent() }.getOrDefault(emptyList())
        }
    }

    fun openTranscript(t: dev.pocketagent.net.TranscriptEntry) {
        val c = gatewayClient ?: return
        scope.launch {
            loading = true; error = null
            try {
                val blocks = c.chat(t.rel, t.src)
                chatBlocks = "${t.src}: ${t.rel.substringAfterLast('/')}" to blocks
            } catch (e: Exception) {
                error = "transcript okunamadı: ${e.message ?: "hata"}"
            } finally {
                loading = false
            }
        }
    }

    // Loopback dev-server önizlemesi (P11: yalnız 127.0.0.0/8 + ::1).
    fun gwPreview(port: Int, path: String) {
        val c = gatewayClient ?: return
        scope.launch {
            loading = true; error = null
            try {
                preview = "127.0.0.1:$port$path" to c.preview("127.0.0.1", port, path)
            } catch (e: Exception) {
                error = "önizleme alınamadı: ${e.message ?: "kapalı?"}"
            } finally {
                loading = false
            }
        }
    }
}
