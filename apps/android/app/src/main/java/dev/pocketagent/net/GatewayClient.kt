// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.net

import dev.pocketagent.transport.GatewayTunnel
import dev.pocketagent.transport.jailOk
import dev.pocketagent.transport.loopbackOk
import org.json.JSONArray

// P11: host gateway istemcisi. Trafik yalnız SSH oturumunun direct-tcpip
// kanalından akar (port-forward dinleyicisi açılmaz); jail kuralları hostla
// aynı şekilde client'ta da uygulanır (defense in depth).
data class GatewayEntry(val name: String, val isDir: Boolean, val size: Long, val mtime: Long)

// P14: /chat yanıt bloğu (role: message|tool|result|error)
data class ChatBlockDto(val role: String, val category: String, val text: String)

// P14: /chat-recent girdisi — allowlist dizinlerindeki transcript kimliği
data class TranscriptEntry(val src: String, val rel: String, val size: Long, val mtime: Long)

class GatewayClient(private val tunnel: GatewayTunnel, private val token: String) {

    suspend fun ls(rel: String = ""): List<GatewayEntry> {
        val clean = rel.trim('/')
        require(clean.isEmpty() || jailOk(clean)) { "traversal rejected" }
        val (status, body) = tunnel.gatewayGet("/ls/$clean", token, 256 * 1024)
        require(status == 200) { "gateway ls $status" }
        return parseLs(body.decodeToString())
    }

    suspend fun readFile(rel: String, maxBytes: Int = 1 shl 20): String {
        require(jailOk(rel)) { "traversal rejected" }
        val (status, body) = tunnel.gatewayGet("/file/$rel", token, maxBytes)
        require(status == 200) { "gateway file $status" }
        return body.decodeToString()
    }

    // kind: staged | unstaged | untracked | working | last (sabit küme, enjeksiyon yok)
    suspend fun diff(kind: String): String {
        require(kind in setOf("staged", "unstaged", "untracked", "working", "last")) { "bad diff kind" }
        val (status, body) = tunnel.gatewayGet("/diff?kind=$kind", token, 1 shl 20)
        require(status == 200) { "gateway diff $status" }
        return body.decodeToString()
    }

    // Loopback dev-server önizlemesi (SSRF gate client'ta da — defense in depth).
    suspend fun preview(host: String, port: Int, path: String): String {
        require(loopbackOk(host)) { "SSRF rejected" }
        require(port in 1..65535) { "bad port" }
        val p = if (path.startsWith("/")) path else "/$path"
        val (status, body) = tunnel.gatewayGet("/preview?h=$host&p=$port&path=$p", token, 1 shl 20)
        require(status == 200) { "gateway preview $status" }
        return body.decodeToString()
    }

    // P14: agent transcript'i (JSONL) → sohbet blokları. src boş: workspace
    // jail'i; "claude"/"codex": hosttaki allowlist dizini (~/.claude/projects,
    // ~/.codex/sessions) — jail bu köklerle ayrı uygulanır.
    suspend fun chat(rel: String, src: String = ""): List<ChatBlockDto> {
        val clean = rel.trim('/')
        require(clean.isNotBlank() && jailOk(clean)) { "traversal rejected" }
        require(src.isEmpty() || src == "claude" || src == "codex") { "bad src" }
        val q = if (src.isEmpty()) "path=$clean" else "src=$src&path=$clean"
        val (status, body) = tunnel.gatewayGet("/chat?$q", token, 1 shl 20)
        require(status == 200) { "gateway chat $status" }
        return parseBlocks(body.decodeToString())
    }

    // Son transcript'ler (allowlist dizinleri; içerik değil kimlik/boyut/zaman)
    suspend fun chatRecent(): List<TranscriptEntry> {
        val (status, body) = tunnel.gatewayGet("/chat-recent", token, 256 * 1024)
        require(status == 200) { "gateway chat-recent $status" }
        val o = org.json.JSONObject(body.decodeToString())
        val arr = o.optJSONArray("transcripts") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val t = arr.optJSONObject(i) ?: return@mapNotNull null
            TranscriptEntry(t.optString("src"), t.optString("rel"), t.optLong("size"), t.optLong("mtime"))
        }.filter { it.rel.isNotBlank() }
    }

    private fun parseBlocks(body: String): List<ChatBlockDto> {
        val o = org.json.JSONObject(body)
        val arr = o.optJSONArray("blocks") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val b = arr.optJSONObject(i) ?: return@mapNotNull null
            ChatBlockDto(b.optString("role"), b.optString("category"), b.optString("text"))
        }
    }

    companion object {
        fun parseLs(body: String): List<GatewayEntry> {
            val arr = JSONArray(body)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                GatewayEntry(
                    name = o.optString("name"),
                    isDir = o.optBoolean("dir"),
                    size = o.optLong("size"),
                    mtime = o.optLong("mtime"),
                )
            }.filter { it.name.isNotBlank() }
        }
    }
}
