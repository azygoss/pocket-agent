// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.data

import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.TerminalTransport
import org.json.JSONArray
import org.json.JSONObject

// Bağlantı + ayar dışa/içe aktarımı. Secret'lar ASLA export'a girmez —
// format sürümü ve şema sabit, bilinmeyen alanlar toleransla atlanır.
object Backup {
    const val VERSION = 1

    fun export(conns: List<SavedConnection>, s: PersistedSettings): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exported_at", System.currentTimeMillis())
        root.put("settings", JSONObject().apply {
            put("font_scale", s.fontScale.toDouble())
            put("backend_url", s.backendUrl)
            put("auto_reconnect", s.autoReconnect)
            put("auto_reconnect_drop", s.autoReconnectOnDrop)
            put("theme_id", s.themeId)
            put("font_id", s.fontId)
            put("snippets", s.snippets)
        })
        val arr = JSONArray()
        conns.forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("host", c.host)
                put("port", c.port)
                put("user", c.user)
                put("auth", if (c.credentialRef.contains("pem")) "pem" else "password")
                put("auto_tmux", c.autoTmux)
            })
        }
        root.put("connections", arr)
        return root.toString(2)
    }

    data class ImportResult(
        val connections: List<SavedConnection>,
        val settings: PersistedSettings?,
        val skipped: Int,
    )

    fun import(json: String): ImportResult {
        val root = JSONObject(json)
        require(root.optInt("version", 0) <= VERSION) { "desteklenmeyen yedek sürümü" }
        val sj = root.optJSONObject("settings")
        val settings = sj?.let {
            PersistedSettings(
                fontScale = it.optDouble("font_scale", 1.0).toFloat(),
                backendUrl = it.optString("backend_url", ""),
                autoReconnect = it.optBoolean("auto_reconnect", false),
                autoReconnectOnDrop = it.optBoolean("auto_reconnect_drop", true),
                themeId = it.optString("theme_id", "pocket"),
                fontId = it.optString("font_id", "jetbrains"),
                snippets = it.optString("snippets", ""),
            )
        }
        val arr = root.optJSONArray("connections") ?: JSONArray()
        val conns = mutableListOf<SavedConnection>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) { skipped++; continue }
            val c = SavedConnection(
                name = o.optString("name", ""),
                host = o.optString("host", ""),
                port = o.optInt("port", 22),
                user = o.optString("user", ""),
                credentialRef = if (o.optString("auth") == "pem") "ram:pem" else "ram:password",
                transportOrder = listOf(TerminalTransport.SSH),
                autoTmux = o.optBoolean("auto_tmux", false),
            )
            if (c.validate().isEmpty()) conns += c else skipped++
        }
        return ImportResult(conns, settings, skipped)
    }
}
