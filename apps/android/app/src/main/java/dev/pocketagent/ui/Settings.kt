// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.runtime.*
import dev.pocketagent.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// P10/P16 zirve: tema + font + terminal paleti + klavye kısayolları tek Settings state.
// store verilirse tercihler kalıcıdır (DataStore); verilmezse in-memory (testler).
data class TerminalPalette(val background: Long, val foreground: Long, val cursor: Long)
data class AppTheme(val dark: Boolean, val fontScale: Float, val palette: TerminalPalette)

val DarkPalette = TerminalPalette(0xFF0D1117, 0xFFC9D1D9, 0xFF58A6FF)
val LightPalette = TerminalPalette(0xFFFFFFFF, 0xFF1F2328, 0xFF0969DA)

class SettingsViewModel(
    private val store: SettingsStore? = null,
    private val scope: CoroutineScope? = null,
) {
    var theme by mutableStateOf(AppTheme(true, 1f, DarkPalette))
        private set

    init {
        if (store != null && scope != null) {
            scope.launch {
                store.load()?.let { (dark, scale) ->
                    theme = AppTheme(dark, scale.coerceIn(0.8f, 2.0f), if (dark) DarkPalette else LightPalette)
                }
            }
        }
    }

    fun toggleDark() {
        theme = theme.copy(dark = !theme.dark, palette = if (theme.dark) LightPalette else DarkPalette)
        persist()
    }

    fun setFontScale(f: Float) {
        theme = theme.copy(fontScale = f.coerceIn(0.8f, 2.0f))
        persist()
    }

    private fun persist() {
        val s = store ?: return
        val sc = scope ?: return
        val t = theme
        sc.launch { s.save(t.dark, t.fontScale) }
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
