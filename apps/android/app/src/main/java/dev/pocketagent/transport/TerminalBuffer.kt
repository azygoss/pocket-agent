// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P10 terminal çekirdeği v2: gerçek ekran modeli (rows×cols hücre ızgarası).
// v1 satır-tabanlıydı; bu sürüm tam ekran uygulamalarını (vim/htop/tmux/less)
// kullanılabilir kılar: imleç konumlama (CUP/CUU/CUD/CUF/CUB), alternate
// screen (?1049/1047/1048), kaydırma bölgesi (DECSTBM), IL/DL, ICH/DCH/ECH,
// ED/EL 0-2, DEC grafik charset'i (ESC ( 0 — tmux çerçeveleri), otomatik
// satır kaydırma (pending-wrap), SGR fg+bg (8/16/256/truecolor), inverse.
// Scrollback yalnız ana ekranda birikir; alt ekran geçicidir (tmux davranışı).

data class TermStyle(
    val fg: Long? = null,
    val bg: Long? = null,
    val bold: Boolean = false,
    val underline: Boolean = false,
)

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

// SGR 7 (inverse) fg/bg takasında tema-bağımsız varsayılanlar.
private const val INVERSE_FG = 0xFFE6EDF3L
private const val INVERSE_BG = 0xFF0D1117L

// DEC özel grafik charset'i (ESC ( 0): tmux/vim çerçeve karakterleri.
private val DEC_GRAPHICS = mapOf(
    '_' to ' ', '`' to '◆', 'a' to '▒', 'b' to '␉', 'c' to '␌', 'd' to '␍',
    'e' to '␊', 'f' to '°', 'g' to '±', 'h' to '␤', 'i' to '␋',
    'j' to '┘', 'k' to '┐', 'l' to '┌', 'm' to '└', 'n' to '┼',
    'o' to '⎺', 'p' to '⎻', 'q' to '─', 'r' to '⎼', 's' to '⎽',
    't' to '├', 'u' to '┤', 'v' to '┴', 'w' to '┬', 'x' to '│',
    'y' to '≤', 'z' to '≥', '{' to 'π', '|' to '≠', '}' to '£', '~' to '·',
)

// Sabit genişlikli satır: hücre bazlı üzerine yazma ucuzdur.
private class RowBuf(val cols: Int) {
    val chars = CharArray(cols) { ' ' }
    val styles = Array(cols) { TermStyle() }

    fun copyFrom(src: RowBuf) {
        val n = minOf(cols, src.cols)
        System.arraycopy(src.chars, 0, chars, 0, n)
        System.arraycopy(src.styles, 0, styles, 0, n)
        if (n < cols) {
            chars.fill(' ', n, cols)
            styles.fill(TermStyle(), n, cols)
        }
    }

    fun isBlank(): Boolean {
        for (i in 0 until cols) if (chars[i] != ' ' || styles[i] != TermStyle()) return false
        return true
    }

    fun erase(range: IntRange, s: TermStyle) {
        val from = range.first.coerceAtLeast(0)
        val to = range.last.coerceAtMost(cols - 1)
        if (from > to) return
        chars.fill(' ', from, to + 1)
        styles.fill(s, from, to + 1)
    }

    fun build(): TermLine {
        var end = cols
        while (end > 0 && chars[end - 1] == ' ' && styles[end - 1] == TermStyle()) end--
        val spans = ArrayList<TermLine.Span>(4)
        var i = 0
        while (i < end) {
            val s = styles[i]
            var j = i + 1
            while (j < end && styles[j] == s) j++
            spans.add(TermLine.Span(String(chars, i, j - i), s))
            i = j
        }
        return TermLine(spans)
    }
}

private class Screen(val rows: Int, val cols: Int) {
    val grid = Array(rows) { RowBuf(cols) }
    var crow = 0
    var ccol = 0
    var savedRow = 0
    var savedCol = 0
    var scrollTop = 0
    var scrollBottom = rows - 1

    fun clampCursor() {
        crow = crow.coerceIn(0, rows - 1)
        ccol = ccol.coerceIn(0, cols - 1)
    }

    // Kullanılan satır sayısı: son boş-olmayan satıra kadar. Ekran tamamen
    // boşsa imleç konumu korunur (en az 1). İmlecin altındaki boş satırlar
    // gösterilmez — prompt sonrası gereksiz boşluk çıkmaz (v1 davranışı).
    fun usedRows(): Int {
        var last = -1
        for (r in rows - 1 downTo 0) {
            if (!grid[r].isBlank()) { last = r; break }
        }
        return if (last < 0) crow + 1 else last + 1
    }
}

class TerminalBuffer(
    private val maxLines: Int = 50_000,
    cols: Int = 80,
    rows: Int = 24,
) {
    var cols = cols
        private set
    var rows = rows
        private set

    private var main = Screen(rows, cols)
    private var alt = Screen(rows, cols)
    private var useAlt = false
    var altScreenActive = false
        private set

    private val scrollback = ArrayDeque<TermLine>()
    private var scrollbackVersion = 0
    private var scrollbackCache: List<TermLine> = emptyList()

    private var style = TermStyle()
    private var inverse = false
    private var decGraphics = false
    private var autowrap = true
    private var wrapPending = false
    private var pending = "" // chunk sınırında bölünen escape dizisi

    var totalFed: Long = 0L
        private set
    var cursorVisible = true
        private set

    val scrollbackSize: Int get() = scrollback.size
    val lineCount: Int get() = (if (useAlt) 0 else scrollback.size) + if (activeIsBlank()) 0 else active().usedRows()

    private fun active() = if (useAlt) alt else main
    private fun activeIsBlank(): Boolean {
        val s = active()
        return s.crow == 0 && s.grid.all { it.isBlank() }
    }

    fun clear() {
        main = Screen(rows, cols)
        alt = Screen(rows, cols)
        useAlt = false
        altScreenActive = false
        scrollback.clear()
        scrollbackVersion++
        scrollbackCache = emptyList()
        style = TermStyle()
        inverse = false
        decGraphics = false
        wrapPending = false
        pending = ""
    }

    fun setScreenSize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        main = resizeScreen(main, newCols, newRows, toScrollback = true)
        alt = resizeScreen(alt, newCols, newRows, toScrollback = false)
        cols = newCols
        rows = newRows
        wrapPending = false
    }

    private fun resizeScreen(old: Screen, c: Int, r: Int, toScrollback: Boolean): Screen {
        val s = Screen(r, c)
        val copyRows = minOf(old.rows, r)
        // Satır küçülmede üstten taşanlar ana ekranda scrollback'e gider.
        val overflow = old.rows - copyRows
        if (toScrollback && overflow > 0) {
            for (i in 0 until overflow) pushScrollback(old.grid[i].build())
        }
        for (i in 0 until copyRows) s.grid[i].copyFrom(old.grid[i + overflow])
        s.crow = old.crow - overflow
        s.ccol = old.ccol
        s.clampCursor()
        return s
    }

    fun snapshot(): List<TermLine> {
        val s = active()
        val used = s.usedRows()
        val screenLines = ArrayList<TermLine>(used)
        for (i in 0 until used) screenLines.add(s.grid[i].build())
        if (useAlt) return screenLines
        return scrollbackSnapshot() + screenLines
    }

    private fun scrollbackSnapshot(): List<TermLine> {
        val v = scrollbackVersion
        val cached = scrollbackCache
        if (cached.size == scrollback.size && v == cachedVersion) return cached
        val fresh = scrollback.toList()
        scrollbackCache = fresh
        cachedVersion = v
        return fresh
    }
    private var cachedVersion = -1

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
                    if (next < 0) { pending = input.substring(i); return }
                    i = next
                }
                '\n', '\u000B', '\u000C' -> { lineFeed(); i++ }
                '\r' -> { active().ccol = 0; wrapPending = false; i++ }
                '\b' -> { val s = active(); if (s.ccol > 0) s.ccol--; wrapPending = false; i++ }
                '\t' -> {
                    val s = active()
                    val next = minOf((s.ccol / 8 + 1) * 8, cols - 1)
                    s.ccol = next
                    wrapPending = false
                    i++
                }
                else -> if (c < ' ' || c == '\u007F') i++ else { putChar(c); i++ }
            }
        }
    }

    private fun putChar(c0: Char) {
        val s = active()
        if (wrapPending) {
            if (autowrap) { lineFeed(); s.ccol = 0 }
            wrapPending = false
        }
        val c = if (decGraphics) DEC_GRAPHICS[c0] ?: c0 else c0
        val eff = if (inverse) {
            TermStyle(fg = style.bg ?: INVERSE_BG, bg = style.fg ?: INVERSE_FG, bold = style.bold, underline = style.underline)
        } else {
            style
        }
        s.grid[s.crow].chars[s.ccol] = c
        s.grid[s.crow].styles[s.ccol] = eff
        if (s.ccol == cols - 1) wrapPending = true else s.ccol++
    }

    // LF/IND: bölge dibindeyse kaydır, değilse imleci indir. CR uygulamaz.
    private fun lineFeed() {
        val s = active()
        if (s.crow == s.scrollBottom) scrollUp(1)
        else if (s.crow < rows - 1) s.crow++
        wrapPending = false
    }

    // RI (ESC M): bölge tepesindeyse aşağı kaydır, değilse imleci çıkar.
    private fun reverseIndex() {
        val s = active()
        if (s.crow == s.scrollTop) scrollDown(1)
        else if (s.crow > 0) s.crow--
        wrapPending = false
    }

    private fun scrollUp(n: Int) {
        val s = active()
        val top = s.scrollTop
        val bottom = s.scrollBottom
        repeat(n.coerceAtMost(bottom - top + 1)) {
            if (!useAlt && top == 0) pushScrollback(s.grid[top].build())
            System.arraycopy(s.grid, top + 1, s.grid, top, bottom - top)
            s.grid[bottom] = RowBuf(cols)
        }
    }

    private fun scrollDown(n: Int) {
        val s = active()
        val top = s.scrollTop
        val bottom = s.scrollBottom
        repeat(n.coerceAtMost(bottom - top + 1)) {
            System.arraycopy(s.grid, top, s.grid, top + 1, bottom - top)
            s.grid[top] = RowBuf(cols)
        }
    }

    private fun pushScrollback(line: TermLine) {
        scrollback.addLast(line)
        scrollbackVersion++
        while (scrollback.size > maxLines) scrollback.removeFirst()
    }

    // ESC dizisini işler, diziden sonraki indeksi döner; dizi chunk sonunda
    // yarım kaldıysa -1 (çağıran pending'e alır).
    private fun escape(s: String, i: Int): Int {
        if (i + 1 >= s.length) return -1
        return when (s[i + 1]) {
            '[' -> csi(s, i + 2)
            ']' -> osc(s, i + 2)
            '(' -> charset(s, i + 2)
            ')' -> if (i + 2 >= s.length) -1 else i + 3 // G1 designator — kullanılmıyor
            '7' -> { val a = active(); a.savedRow = a.crow; a.savedCol = a.ccol; i + 2 }
            '8' -> { val a = active(); a.crow = a.savedRow; a.ccol = a.savedCol; a.clampCursor(); i + 2 }
            'D' -> { lineFeed(); i + 2 }
            'E' -> { active().ccol = 0; lineFeed(); i + 2 }
            'M' -> { reverseIndex(); i + 2 }
            'c' -> { resetSoft(); i + 2 }
            else -> i + 2
        }
    }

    private fun charset(s: String, i: Int): Int {
        if (i >= s.length) return -1
        decGraphics = s[i] == '0'
        return i + 1
    }

    private fun resetSoft() {
        val s = active()
        for (r in 0 until rows) s.grid[r] = RowBuf(cols)
        s.crow = 0; s.ccol = 0; s.scrollTop = 0; s.scrollBottom = rows - 1
        style = TermStyle()
        inverse = false
        decGraphics = false
        wrapPending = false
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

    private fun handleCsi(final: Char, raw: String) {
        if (raw.startsWith('?')) { privateMode(final, raw.substring(1)); return }
        val parts = raw.split(';')
        fun p(idx: Int, default: Int): Int =
            parts.getOrNull(idx)?.toIntOrNull() ?: default
        val s = active()
        when (final) {
            'm' -> sgr(raw)
            'A' -> { s.crow = (s.crow - p(0, 1)).coerceAtLeast(0); wrapPending = false }
            'B', 'e' -> { s.crow = (s.crow + p(0, 1)).coerceAtMost(rows - 1); wrapPending = false }
            'C' -> { s.ccol = (s.ccol + p(0, 1)).coerceAtMost(cols - 1); wrapPending = false }
            'D' -> { s.ccol = (s.ccol - p(0, 1)).coerceAtLeast(0); wrapPending = false }
            'E' -> { s.crow = (s.crow + p(0, 1)).coerceAtMost(rows - 1); s.ccol = 0; wrapPending = false }
            'F' -> { s.crow = (s.crow - p(0, 1)).coerceAtLeast(0); s.ccol = 0; wrapPending = false }
            'G', '`' -> { s.ccol = (p(0, 1) - 1).coerceIn(0, cols - 1); wrapPending = false }
            'd' -> { s.crow = (p(0, 1) - 1).coerceIn(0, rows - 1); wrapPending = false }
            'H', 'f' -> {
                s.crow = (p(0, 1) - 1).coerceIn(0, rows - 1)
                s.ccol = (p(1, 1) - 1).coerceIn(0, cols - 1)
                wrapPending = false
            }
            'J' -> eraseDisplay(p(0, 0))
            'K' -> eraseLine(p(0, 0))
            'L' -> insertLines(p(0, 1))
            'M' -> deleteLines(p(0, 1))
            'P' -> deleteChars(p(0, 1))
            '@' -> insertChars(p(0, 1))
            'S' -> scrollUp(p(0, 1))
            'T' -> scrollDown(p(0, 1))
            'X' -> s.grid[s.crow].erase(s.ccol until (s.ccol + p(0, 1)), TermStyle(bg = style.bg))
            'r' -> {
                val top = (p(0, 1) - 1).coerceIn(0, rows - 1)
                val bottom = (p(1, rows) - 1).coerceIn(0, rows - 1)
                if (top < bottom) { s.scrollTop = top; s.scrollBottom = bottom }
                s.crow = 0; s.ccol = 0; wrapPending = false
            }
            's' -> { s.savedRow = s.crow; s.savedCol = s.ccol }
            'u' -> { s.crow = s.savedRow; s.ccol = s.savedCol; s.clampCursor(); wrapPending = false }
            else -> {} // t/h/l dışı modlar, raporlar, fare: yoksay
        }
    }

    private fun privateMode(final: Char, raw: String) {
        val mode = raw.toIntOrNull() ?: return
        when (mode) {
            1049 -> if (final == 'h') enterAlt(saveCursor = true, clear = true) else exitAlt(restoreCursor = true)
            1047 -> if (final == 'h') enterAlt(saveCursor = false, clear = false) else exitAlt(restoreCursor = false)
            1048 -> { // yalnız imleç kaydet/geri yükle
                val s = active()
                if (final == 'h') { s.savedRow = s.crow; s.savedCol = s.ccol }
                else { s.crow = s.savedRow; s.ccol = s.savedCol; s.clampCursor() }
            }
            47 -> if (final == 'h') enterAlt(saveCursor = false, clear = false) else exitAlt(restoreCursor = false)
            25 -> cursorVisible = final == 'h'
            7 -> autowrap = final == 'h'
            else -> {} // 1 (app cursor), 2004 (bracketed paste) vb: yoksay
        }
    }

    private fun enterAlt(saveCursor: Boolean, clear: Boolean) {
        if (useAlt) return
        if (saveCursor) { main.savedRow = main.crow; main.savedCol = main.ccol }
        if (clear) alt = Screen(rows, cols)
        useAlt = true
        altScreenActive = true
        wrapPending = false
    }

    private fun exitAlt(restoreCursor: Boolean) {
        if (!useAlt) return
        useAlt = false
        altScreenActive = false
        if (restoreCursor) { main.crow = main.savedRow; main.ccol = main.savedCol; main.clampCursor() }
        wrapPending = false
    }

    private fun eraseDisplay(mode: Int) {
        val s = active()
        val blank = TermStyle(bg = style.bg)
        when (mode) {
            0 -> {
                s.grid[s.crow].erase(s.ccol until cols, blank)
                for (r in s.crow + 1 until rows) s.grid[r].erase(0 until cols, blank)
            }
            1 -> {
                for (r in 0 until s.crow) s.grid[r].erase(0 until cols, blank)
                s.grid[s.crow].erase(0..s.ccol, blank)
            }
            2 -> for (r in 0 until rows) s.grid[r].erase(0 until cols, blank)
            3 -> { scrollback.clear(); scrollbackVersion++ } // xterm: scrollback temizle
        }
    }

    private fun eraseLine(mode: Int) {
        val s = active()
        val blank = TermStyle(bg = style.bg)
        when (mode) {
            0 -> s.grid[s.crow].erase(s.ccol until cols, blank)
            1 -> s.grid[s.crow].erase(0..s.ccol, blank)
            2 -> s.grid[s.crow].erase(0 until cols, blank)
        }
    }

    private fun insertLines(n: Int) {
        val s = active()
        if (s.crow !in s.scrollTop..s.scrollBottom) return
        val cnt = n.coerceAtMost(s.scrollBottom - s.crow + 1)
        repeat(cnt) {
            System.arraycopy(s.grid, s.crow, s.grid, s.crow + 1, s.scrollBottom - s.crow)
            s.grid[s.crow] = RowBuf(cols)
        }
    }

    private fun deleteLines(n: Int) {
        val s = active()
        if (s.crow !in s.scrollTop..s.scrollBottom) return
        val cnt = n.coerceAtMost(s.scrollBottom - s.crow + 1)
        repeat(cnt) {
            System.arraycopy(s.grid, s.crow + 1, s.grid, s.crow, s.scrollBottom - s.crow)
            s.grid[s.scrollBottom] = RowBuf(cols)
        }
    }

    private fun deleteChars(n: Int) {
        val s = active()
        val row = s.grid[s.crow]
        val cnt = n.coerceAtMost(cols - s.ccol)
        System.arraycopy(row.chars, s.ccol + cnt, row.chars, s.ccol, cols - s.ccol - cnt)
        System.arraycopy(row.styles, s.ccol + cnt, row.styles, s.ccol, cols - s.ccol - cnt)
        row.erase(cols - cnt until cols, TermStyle(bg = style.bg))
    }

    private fun insertChars(n: Int) {
        val s = active()
        val row = s.grid[s.crow]
        val cnt = n.coerceAtMost(cols - s.ccol)
        System.arraycopy(row.chars, s.ccol, row.chars, s.ccol + cnt, cols - s.ccol - cnt)
        System.arraycopy(row.styles, s.ccol, row.styles, s.ccol + cnt, cols - s.ccol - cnt)
        row.erase(s.ccol until s.ccol + cnt, TermStyle(bg = style.bg))
    }

    private fun sgr(params: String) {
        val parts = if (params.isEmpty()) listOf(0) else params.split(';').map { it.toIntOrNull() ?: 0 }
        var i = 0
        while (i < parts.size) {
            when (val p = parts[i]) {
                0 -> { style = TermStyle(); inverse = false }
                1 -> style = style.copy(bold = true)
                22 -> style = style.copy(bold = false)
                4 -> style = style.copy(underline = true)
                24 -> style = style.copy(underline = false)
                7 -> inverse = true
                27 -> inverse = false
                39 -> style = style.copy(fg = null)
                49 -> style = style.copy(bg = null)
                in 30..37 -> style = style.copy(fg = ANSI_COLORS[p - 30])
                in 90..97 -> style = style.copy(fg = ANSI_BRIGHT[p - 90])
                in 40..47 -> style = style.copy(bg = ANSI_COLORS[p - 40])
                in 100..107 -> style = style.copy(bg = ANSI_BRIGHT[p - 100])
                38, 48 -> { // genişletilmiş: 38;5;n / 38;2;r;g;b (ve bg karşılığı 48)
                    val target = if (p == 38) true else false
                    if (i + 2 < parts.size && parts[i + 1] == 5) {
                        val c = xterm256(parts[i + 2])
                        style = if (target) style.copy(fg = c) else style.copy(bg = c)
                        i += 2
                    } else if (i + 4 < parts.size && parts[i + 1] == 2) {
                        val r = parts[i + 2]; val g = parts[i + 3]; val b = parts[i + 4]
                        val c = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                        style = if (target) style.copy(fg = c) else style.copy(bg = c)
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
