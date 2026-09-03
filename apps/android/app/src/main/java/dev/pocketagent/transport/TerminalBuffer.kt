// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P10 terminal çekirdeği: satır-tabanlı, stilli scrollback. SGR renkleri,
// CR ile satır-üstüne yazma (progress bar/readline), backspace, tab, EL/J
// silmeleri ve imleç kolon hareketleri (C/D/G) desteklenir. Tam VT100
// (alternate screen, satır-scroll bölgeleri) termlib gelene kadar kapsam dışı.
data class TermStyle(val fg: Long? = null, val bold: Boolean = false, val underline: Boolean = false)

data class TermLine(val spans: List<Span>) {
    data class Span(val text: String, val style: TermStyle)
    val text: String get() = spans.joinToString("") { it.text }
}

// Standart ANSI paleti (koyu terminal zeminine göre ayarlı).
private val ANSI_COLORS = longArrayOf(
    0xFF484F58, 0xFFF85149, 0xFF3FB950, 0xFFD29922,
    0xFF58A6FF, 0xFFBB9AF7, 0xFF39C5CF, 0xFFB1BAC4,
)
private val ANSI_BRIGHT = longArrayOf(
    0xFF6E7681, 0xFFFF7B72, 0xFF56D364, 0xFFE3B341,
    0xFF79C0FF, 0xFFD2A8FF, 0xFF56D4DD, 0xFFFFFFFF,
)

private class LineCursor {
    val chars = ArrayList<Char>(128)
    val styles = ArrayList<TermStyle>(128)
    var col = 0

    fun put(c: Char, s: TermStyle) {
        while (chars.size <= col) { chars.add(' '); styles.add(TermStyle()) }
        chars[col] = c
        styles[col] = s
        col++
    }

    fun eraseFromCursor() {
        while (chars.size > col) { chars.removeAt(chars.size - 1); styles.removeAt(styles.size - 1) }
    }

    fun eraseToCursor() {
        var i = 0
        while (i < col && i < chars.size) { chars[i] = ' '; styles[i] = TermStyle(); i++ }
    }

    fun eraseAll() { chars.clear(); styles.clear(); col = 0 }

    fun build(): TermLine {
        // sondaki stilsiz boşlukları at
        var end = chars.size
        while (end > 0 && chars[end - 1] == ' ' && styles[end - 1] == TermStyle()) end--
        val spans = ArrayList<TermLine.Span>(4)
        var i = 0
        while (i < end) {
            val s = styles[i]
            var j = i + 1
            while (j < end && styles[j] == s) j++
            spans.add(TermLine.Span(String(chars.toCharArray(), i, j - i), s))
            i = j
        }
        return TermLine(spans)
    }
}

class TerminalBuffer(private val maxLines: Int = 50_000) {
    private val done = ArrayDeque<TermLine>()
    private var cur = LineCursor()
    private var style = TermStyle()
    private var pending = "" // chunk sınırında bölünen escape dizisi
    var totalFed: Long = 0L
        private set

    val lineCount: Int get() = done.size + if (cur.col > 0) 1 else 0

    fun clear() {
        done.clear()
        cur = LineCursor()
    }

    fun snapshot(): List<TermLine> {
        val b = cur.build()
        // Tamamen boş imleç satırı listede yer kaplamasın
        return if (b.text.isEmpty() && done.isNotEmpty()) done.toList() else done.toList() + b
    }

    fun feed(chunk: String) {
        totalFed += chunk.length
        val input = pending + chunk
        pending = ""
        var i = 0
        val n = input.length
        while (i < n) {
            when (val c = input[i]) {
                '\u001B' -> {
                    val next = escape(input, i)
                    if (next < 0) { pending = input.substring(i); return } // bölünmüş dizi
                    i = next
                }
                '\n' -> { newLine(); i++ }
                '\r' -> { cur.col = 0; i++ }
                '\b' -> { if (cur.col > 0) cur.col--; i++ }
                '\t' -> { repeat(8 - (cur.col % 8)) { cur.put(' ', style) }; i++ }
                else -> if (c < ' ' || c == '\u007F') i++ else { cur.put(c, style); i++ }
            }
        }
    }

    private fun newLine() {
        done.addLast(cur.build())
        cur = LineCursor()
        while (done.size > maxLines) done.removeFirst()
    }

    // ESC dizisini işler, diziden sonraki indeksi döner; dizi chunk sonunda
    // yarım kaldıysa -1 (çağıran pending'e alır).
    private fun escape(s: String, i: Int): Int {
        if (i + 1 >= s.length) return -1
        return when (s[i + 1]) {
            '[' -> csi(s, i + 2)
            ']' -> osc(s, i + 2)
            '(', ')' -> if (i + 2 >= s.length) -1 else i + 3 // charset designator
            else -> i + 2 // ESC + tek karakter
        }
    }

    private fun osc(s: String, i: Int): Int {
        var j = i
        while (j < s.length) {
            if (s[j] == '\u0007') return j + 1
            if (s[j] == '\u001B' && j + 1 < s.length && s[j + 1] == '\\') return j + 2
            j++
        }
        return -1
    }

    private fun csi(s: String, start: Int): Int {
        var j = start
        while (j < s.length) {
            val f = s[j]
            if (f in '@'..'~') {
                handleCsi(f, s.substring(start, j))
                return j + 1
            }
            j++
        }
        return -1
    }

    private fun handleCsi(final: Char, paramsRaw: String) {
        val clean = paramsRaw.trimStart('?', '>', '!')
        when (final) {
            'm' -> sgr(clean)
            'K' -> when (clean.toIntOrNull() ?: 0) {
                0 -> cur.eraseFromCursor()
                1 -> cur.eraseToCursor()
                2 -> cur.eraseAll()
            }
            'J' -> if ((clean.toIntOrNull() ?: 0) >= 2) { done.clear(); cur.eraseAll() }
            'C' -> cur.col += clean.toIntOrNull() ?: 1
            'D' -> cur.col = (cur.col - (clean.toIntOrNull() ?: 1)).coerceAtLeast(0)
            'G', '`' -> cur.col = ((clean.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
            'X' -> repeat(clean.toIntOrNull() ?: 1) { cur.put(' ', TermStyle()) }
            // A/B/E/F/H/f/s/u/r/d/e: satır konumu — satır-tabanlı modelde yoksay
            else -> {}
        }
    }

    private fun sgr(params: String) {
        val parts = if (params.isEmpty()) listOf(0) else params.split(';').map { it.toIntOrNull() ?: 0 }
        var i = 0
        while (i < parts.size) {
            when (val p = parts[i]) {
                0 -> style = TermStyle()
                1 -> style = style.copy(bold = true)
                22 -> style = style.copy(bold = false)
                4 -> style = style.copy(underline = true)
                24 -> style = style.copy(underline = false)
                39 -> style = style.copy(fg = null)
                in 30..37 -> style = style.copy(fg = ANSI_COLORS[p - 30])
                in 90..97 -> style = style.copy(fg = ANSI_BRIGHT[p - 90])
                38 -> { // genişletilmiş: 38;5;n veya 38;2;r;g;b
                    if (i + 2 < parts.size && parts[i + 1] == 5) {
                        style = style.copy(fg = xterm256(parts[i + 2]))
                        i += 2
                    } else if (i + 4 < parts.size && parts[i + 1] == 2) {
                        val r = parts[i + 2]; val g = parts[i + 3]; val b = parts[i + 4]
                        style = style.copy(fg = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
                        i += 4
                    }
                }
                else -> {}
            }
            i++
        }
    }

    private fun xterm256(n: Int): Long = when {
        n < 8 -> ANSI_COLORS[n]
        n < 16 -> ANSI_BRIGHT[n - 8]
        n in 16..231 -> { // 6x6x6 küp
            val v = n - 16
            val r = v / 36; val g = (v % 36) / 6; val b = v % 6
            fun cv(x: Int) = if (x == 0) 0 else 55 + x * 40
            0xFF000000L or (cv(r).toLong() shl 16) or (cv(g).toLong() shl 8) or cv(b).toLong()
        }
        else -> { // gri skala
            val g = 8 + (n - 232) * 10
            0xFF000000L or (g.toLong() shl 16) or (g.toLong() shl 8) or g.toLong()
        }
    }
}
