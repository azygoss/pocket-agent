// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.runtime.*
import dev.pocketagent.data.PersistedSettings
import dev.pocketagent.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// P10/P16 zirve: tema + font + terminal paleti + klavye kısayolları tek Settings state.
// store verilirse tercihler kalıcıdır (DataStore); verilmezse in-memory (testler).
data class AppTheme(
    val themeId: String = "pocket",
    val fontId: String = "jetbrains",
    val fontScale: Float = 1f,
)

class SettingsViewModel(
    private val store: SettingsStore? = null,
    private val scope: CoroutineScope? = null,
) {
    var theme by mutableStateOf(AppTheme())
        private set
    var backendUrl by mutableStateOf("")
        private set
    var tenantToken by mutableStateOf("")
        private set

    var autoReconnect by mutableStateOf(false)
        private set
    // Kopmada otomatik yeniden bağlan (varsayılan açık)
    var autoReconnectOnDrop by mutableStateOf(true)
        private set
    // Tuş şeridi snippet'ları: her satır "etiket=komut".
    var snippets by mutableStateOf("")
        private set

    init {
        if (store != null && scope != null) {
            scope.launch {
                store.load()?.let { p ->
                    autoReconnect = p.autoReconnect
                    autoReconnectOnDrop = p.autoReconnectOnDrop
                    theme = AppTheme(p.themeId, p.fontId, p.fontScale.coerceIn(0.8f, 2.0f))
                    backendUrl = p.backendUrl
                    tenantToken = p.tenantToken
                    snippets = p.snippets
                }
            }
        }
    }

    fun setThemeId(id: String) {
        theme = theme.copy(themeId = id)
        persist()
    }

    fun setFontId(id: String) {
        theme = theme.copy(fontId = id)
        persist()
    }

    fun toggleAutoReconnect() {
        autoReconnect = !autoReconnect
        persist()
    }

    fun toggleAutoReconnectOnDrop() {
        autoReconnectOnDrop = !autoReconnectOnDrop
        persist()
    }

    fun setFontScale(f: Float) {
        theme = theme.copy(fontScale = f.coerceIn(0.8f, 2.0f))
        persist()
    }

    fun setBackend(url: String, tenant: String) {
        backendUrl = url.trim()
        tenantToken = tenant.trim()
        persist()
    }

    fun updateSnippets(v: String) {
        snippets = v
        persist()
    }

    // Yedekten dönen ayar setini toplu uygula (tenant token korunur —
    // export'a girmez, mevcut değer ezilmez).
    fun applyAll(p: PersistedSettings) {
        theme = AppTheme(p.themeId, p.fontId, p.fontScale.coerceIn(0.8f, 2.0f))
        if (p.backendUrl.isNotBlank()) backendUrl = p.backendUrl
        autoReconnect = p.autoReconnect
        autoReconnectOnDrop = p.autoReconnectOnDrop
        snippets = p.snippets
        persist()
    }

    // "gs=git status\nht=htop" → (etiket, gönderilecek metin) çiftleri.
    // Komut sonuna \n eklenmez — kullanıcı Enter'a basar (iptal şansı kalır).
    fun snippetList(): List<Pair<String, String>> =
        snippets.lines().mapNotNull { l ->
            val i = l.indexOf('=')
            if (i <= 0) null else l.take(i).trim() to l.substring(i + 1)
        }.filter { it.first.isNotBlank() && it.second.isNotBlank() }

    val backendConfigured: Boolean
        get() = backendUrl.isNotBlank() && tenantToken.isNotBlank()

    private fun persist() {
        val s = store ?: return
        val sc = scope ?: return
        val snap = PersistedSettings(theme.fontScale, backendUrl, tenantToken, autoReconnect, autoReconnectOnDrop, theme.themeId, theme.fontId, snippets)
        sc.launch { s.save(snap) }
    }
}

// P10: özel kısayol editörü + donanım klavye eşlemesi.
data class Shortcut(val label: String, val sequence: String)

class ShortcutModel {
    private val _custom = mutableStateListOf<Shortcut>()
    val custom: List<Shortcut> get() = _custom
    fun add(label: String, seq: String): Boolean {
        if (label.isBlank() || seq.isBlank() || _custom.any { it.sequence == seq }) return false
        _custom.add(Shortcut(label, seq)); return true
    }
    fun remove(seq: String) { _custom.removeAll { it.sequence == seq } }
    fun tmuxPrefix(): String = "Ctrl-b"
}
