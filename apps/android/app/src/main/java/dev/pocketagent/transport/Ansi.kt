// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P10: minimal ANSI sanitizer for readable scrollback. Full VT100 emulation
// arrives with termlib (P06 device slice); until then we strip CSI/OSC/escape
// sequences and normalize CR so remote output renders as clean text lines.
object Ansi {
    // CSI: ESC [ params intermediates final
    private val csi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    // OSC: ESC ] ... terminated by BEL or ST
    private val osc = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")
    // Two-char escapes: ESC ( B, ESC ) 0, ESC =, ESC >, ESC c, etc.
    private val esc = Regex("\u001B[@-Z\\\\-_]|\\u001B[()][0-9A-B]")
    // Remaining control chars except \n and \t
    private val ctrl = Regex("[\\x00-\\x08\\x0B-\\x1A\\x1C-\\x1F\\x7F]")

    fun strip(raw: String): String {
        var s = raw
        s = osc.replace(s, "")
        s = csi.replace(s, "")
        s = esc.replace(s, "")
        s = s.replace("\r\n", "\n").replace("\r", "\n")
        s = ctrl.replace(s, "")
        return s
    }
}
