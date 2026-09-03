// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.runtime.*

// P10/P16 zirve: tema + font + terminal paleti + klavye kısayolları tek Settings state.
data class TerminalPalette(val background: Long, val foreground: Long, val cursor: Long)
data class AppTheme(val dark: Boolean, val fontScale: Float, val palette: TerminalPalette)

val DarkPalette = TerminalPalette(0xFF0D1117, 0xFFC9D1D9, 0xFF58A6FF)
val LightPalette = TerminalPalette(0xFFFFFFFF, 0xFF1F2328, 0xFF0969DA)

class SettingsViewModel {
    var theme by mutableStateOf(AppTheme(true, 1f, DarkPalette))
        private set
    fun toggleDark() { theme = theme.copy(dark = !theme.dark, palette = if (theme.dark) LightPalette else DarkPalette) }
    fun setFontScale(f: Float) { theme = theme.copy(fontScale = f.coerceIn(0.8f, 2.0f)) }
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
