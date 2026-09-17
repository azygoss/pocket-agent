// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.TerminalBuffer
import org.junit.Assert.*
import org.junit.Test

// TerminalBuffer v2 (ekran modeli). Not: PTY çıktısı gerçekte CRLF'dir
// (ONLCR); LF tek başına yalnız aşağı iner, sütunu sıfırlamaz.
class TerminalBufferTest {
    @Test fun plainLinesAndPartialLine() {
        val b = TerminalBuffer()
        b.feed("hello")
        assertEquals("hello", b.snapshot().single().text)
        b.feed(" world\r\nnext\r\n")
        assertEquals(listOf("hello world", "next"), b.snapshot().map { it.text })
    }

    @Test fun lfOnlyMovesDownKeepsColumn() {
        val b = TerminalBuffer()
        b.feed("ab\ncd") // CR yok: ikinci satır sütun 2'den başlar (gerçek PTY davranışı)
        assertEquals(listOf("ab", "  cd"), b.snapshot().map { it.text })
    }

    @Test fun carriageReturnOverwrites() {
        val b = TerminalBuffer()
        b.feed("progress 10%\rprogress 90%\r\n")
        assertEquals("progress 90%", b.snapshot().single().text)
    }

    @Test fun backspaceEdits() {
        val b = TerminalBuffer()
        b.feed("abc\b\bXY")
        assertEquals("aXY", b.snapshot().single().text)
    }

    @Test fun sgrColorsBecomeSpans() {
        val b = TerminalBuffer()
        b.feed("\u001B[31mred\u001B[0m plain\r\n")
        val line = b.snapshot().single()
        assertEquals("red plain", line.text)
        assertEquals(2, line.spans.size)
        assertEquals(0xFFE6676B, line.spans[0].style.fg)
        assertNull(line.spans[1].style.fg)
    }

    @Test fun boldAndBrightAnd256() {
        val b = TerminalBuffer()
        b.feed("\u001B[1;96mhi\u001B[0m\r\n")
        val s = b.snapshot().single().spans.single().style
        assertTrue(s.bold)
        assertEquals(0xFF7ADED5, s.fg) // bright cyan
        val b2 = TerminalBuffer()
        b2.feed("\u001B[38;5;196mx\r\n")
        assertEquals(0xFFFF0000, b2.snapshot().single().spans.single().style.fg) // kırmızı küp
    }

    @Test fun backgroundColors() {
        val b = TerminalBuffer()
        b.feed("\u001B[41;97mhi\u001B[0m\r\n")
        val s = b.snapshot().single().spans.single().style
        assertEquals(0xFFE6676B, s.bg) // kırmızı zemin
        assertEquals(0xFFFFFFFF, s.fg) // parlak beyaz
        val b2 = TerminalBuffer()
        b2.feed("\u001B[48;2;10;20;30mx\r\n")
        assertEquals(0xFF0A141E, b2.snapshot().single().spans.single().style.bg)
    }

    @Test fun inverseVideoSwapsColors() {
        val b = TerminalBuffer()
        b.feed("\u001B[7mhi\u001B[27m\r\n")
        val s = b.snapshot().single().spans.single().style
        assertNotNull(s.bg) // fg ↔ bg takas edildi
        assertNotNull(s.fg)
    }

    @Test fun eraseLineAndCursorMoves() {
        val b = TerminalBuffer()
        b.feed("abcdef\u001B[3DZZ") // 3 geri, üstüne yaz
        assertEquals("abcZZf", b.snapshot().single().text)
        val b2 = TerminalBuffer()
        b2.feed("junk\r\u001B[Kclean\r\n")
        assertEquals("clean", b2.snapshot().single().text)
    }

    @Test fun cursorPositioningAndClamp() {
        val b = TerminalBuffer(cols = 40, rows = 6)
        b.feed("\u001B[3;10Hhi") // 3. satır 10. sütun (1-based)
        val lines = b.snapshot().map { it.text }
        assertEquals(3, lines.size)
        assertEquals("", lines[0])
        assertEquals("", lines[1])
        assertEquals("         hi", lines[2])
        b.feed("\u001B[999;999HZ") // ekran dışı → clamp, tek karakter
        val last = b.snapshot().last().text
        assertTrue("son satır Z ile bitmeli: '$last'", last.endsWith("Z"))
    }

    @Test fun altScreenSwapsAndRestores() {
        val b = TerminalBuffer(cols = 40, rows = 4)
        b.feed("main line\r\n")
        b.feed("\u001B[?1049h")
        assertTrue(b.altScreenActive)
        b.feed("\u001B[1;1HALT MODE")
        assertEquals(listOf("ALT MODE"), b.snapshot().map { it.text })
        b.feed("\u001B[?1049l")
        assertFalse(b.altScreenActive)
        assertEquals(listOf("main line"), b.snapshot().map { it.text })
    }

    @Test fun altScreenCursorSurvivesResize() {
        // Ajan (codex vb.) açıkken klavye açılınca viewport küçülür; ?1049
        // çıkışında kayıtlı imleç korunmazsa prompt eski satırların üstüne yazar.
        val b = TerminalBuffer(cols = 40, rows = 10)
        for (i in 0 until 9) b.feed("l$i\r\n") // imleç satır 9
        b.feed("\u001B[?1049h")
        b.feed("\u001B[1;1HAGENT UI")
        b.setScreenSize(40, 8) // 2 satır scrollback'e taşar
        b.feed("\u001B[?1049l")
        assertFalse(b.altScreenActive)
        assertEquals(9, b.cursorPosition()!!.first) // 0 değil
        assertEquals("l8", b.snapshot().last().text)
    }

    @Test fun altExitCursorStaysBelowLeftoverContent() {
        // TUI/agent kapanınca geri yüklenen imleç kalan metnin içine
        // düşmemeli — yeni çıktı son dolu satırın altından devam eder.
        val b = TerminalBuffer(cols = 40, rows = 10)
        b.feed("prompt$\r\n") // imleç satır 1 (savedRow)
        b.feed("\u001B[?1049h")
        b.feed("\u001B[1;1HAGENT")
        // Agent çalışırken ana ekrana içerik geldiğini simüle et —
        // pratikte remote resize ile main kaydı; burada doğrudan kurulur:
        b.feed("\u001B[?1049l")
        // Çıkış sonrası imleç içerik satırında/altında olmalı, üstüne yazmamalı.
        val cur = b.cursorPosition()!!.first
        val snap = b.snapshot()
        assertTrue("imleç içerik üstünde olmamalı: cur=$cur", cur >= snap.size - 1 || snap[cur].text.isBlank())
        // Sonraki yazı boş satıra düşmeli:
        b.feed("next")
        assertEquals("next", b.snapshot().last().text.trimEnd())
    }

    @Test fun combinedPrivateModesParsed() {
        // DEC private modlar noktalı virgülle birleşebilir: ?25;1049l tek
        // dizide hem imleci gizler hem alt ekrandan çıkar.
        val b = TerminalBuffer(cols = 40, rows = 4)
        b.feed("\u001B[?1049h")
        assertTrue(b.altScreenActive)
        b.feed("\u001B[?25;1049l")
        assertFalse(b.altScreenActive)
        assertFalse(b.cursorVisible)
    }

    @Test fun softResetRearmsStaleCursorGuardInSameFrame() {
        // Inline TUI çıkışı tek SSH frame'inde gelir: konumlandırma + CSI ! p
        // + prompt. cursorPositioned reset'te düşürülmezse prompt eski
        // metnin üstüne yazar; düşürülünce kelepçe prompt'u alta taşır.
        val b = TerminalBuffer(cols = 40, rows = 10)
        b.feed("agent satır 1\r\nagent satır 2\r\nagent satır 3\r\n")
        b.feed("\u001B[2;1H\u001B[!proot@devbox:~# ")
        val lines = b.snapshot().map { it.text }
        assertEquals("agent satır 2", lines[1].trimEnd())
        assertTrue("prompt içerik altında olmalı: $lines", lines[3].startsWith("root@devbox"))
    }

    @Test fun softResetRestoresScrollRegion() {
        // Inline TUI çıkışı (DECSRR): daraltılmış scroll bölgesi sıfırlanmazsa
        // imleç eski metnin içinde hapsolur — pi/claude tarzı agent'ların
        // "kapatınca yazılar içinde kalma" belirtisi.
        val b = TerminalBuffer(cols = 40, rows = 10)
        for (i in 0 until 10) b.feed("l$i\r\n") // ekran dolar, l0 scrollback'e
        val sb0 = b.scrollbackSize
        b.feed("\u001B[4;9r") // bölge daralt (3..8) — DECSTBM imleci 0,0'a götürür
        b.feed("\u001B[!p")   // soft reset → bölge full
        b.feed("\u001B[10;1H\r\nX") // dipten LF → tam bölge kayar → scrollback +1
        assertEquals(sb0 + 1, b.scrollbackSize)
    }

    @Test fun repRepeatsLastChar() {
        val b = TerminalBuffer(cols = 40, rows = 5)
        b.feed("x\u001B[4b") // x + 4 kez x
        assertEquals("xxxxx", b.snapshot().first().text)
    }

    @Test fun deadInlineTuiPromptSnapsBelowContent() {
        // pi/codex tarzı inline TUI: çerçeveyi ana ekrana çizer, çıkarken
        // imleci metnin ortasında bırakır (temizlik dizisi gelmez). Sonraki
        // kabuk prompt'u tüm metnin ALTINDAN devam etmeli, ezmemeli.
        val b = TerminalBuffer(cols = 40, rows = 10)
        b.feed("agent satır 1\r\nagent satır 2\r\nagent satır 3\r\n")
        b.feed("\u001B[2;1H") // TUI'nin son çizimi imleci içeriğe taşıdı
        b.feed("root@devbox:~# ") // ölü TUI sonrası: düz metin chunk'ı
        val lines = b.snapshot().map { it.text }
        assertEquals("agent satır 3", lines[2].trimEnd())
        assertTrue("prompt içerik altında olmalı: $lines", lines[3].startsWith("root@devbox"))
    }

    @Test fun deadTuiNarrowedRegionIsReleased() {
        // Agent çıkışında bölge daraltılmış + imleç içeride kalmış:
        // bölge sıfırlanıp imleç tüm içeriğin altına taşınmalı.
        val b = TerminalBuffer(cols = 20, rows = 6)
        b.feed("a\r\nb\r\nc\r\nd\r\ne\r\nf")
        b.feed("\u001B[2;5r\u001B[3;1H") // bölge daralt + imleç içeride
        b.feed("PROMPT") // ölü TUI sonrası düz metin
        val lines = b.snapshot().map { it.text }
        assertTrue("prompt en sonda olmalı: $lines", lines.last().startsWith("PROMPT"))
        assertEquals(1, b.scrollbackSize) // dolu ekran bir satır yer açtı
    }

    @Test fun positionedWritesInsideContentNotSnapped() {
        // Canlı TUI: aynı chunk'ta konumlandırma + içerik-içi yazı serbest
        // kalmalı — kelepçe yalnız konumlandırmasız düz metni yakalar.
        val b = TerminalBuffer(cols = 40, rows = 10)
        b.feed("one\r\ntwo\r\nthree\r\n")
        b.feed("\u001B[2;1HZZ")
        assertEquals("ZZo", b.snapshot()[1].text)
    }

    @Test fun sgrStoresAnsiIndexForTheming() {
        // Renk indeksi saklanır ki tema değişince çıktı yeni paletten çözülsün.
        val b = TerminalBuffer()
        b.feed("\u001B[31mred\u001B[38;5;208morange\u001B[38;2;1;2;3mtrue")
        val spans = b.snapshot().single().spans
        assertEquals(1, spans[0].style.fgIndex)
        assertEquals(208, spans[1].style.fgIndex)
        assertNull(spans[2].style.fgIndex) // truecolor: indeks yok, fg doğrudan
    }

    @Test fun decGraphicsCharset() {
        val b = TerminalBuffer()
        b.feed("\u001B[?1049h\u001B[1;1H\u001B(0lqqk\u001B(B") // DEC graphics: ┌──┐
        assertEquals("┌──┐", b.snapshot().first().text)
    }

    @Test fun insertAndDeleteLines() {
        val b = TerminalBuffer(cols = 20, rows = 5)
        b.feed("r0\r\nr1\r\nr2\r\nr3")
        b.feed("\u001B[2;1H\u001B[L") // 2. satıra boş satır ekle → r1..r3 aşağı kayar
        assertEquals(listOf("r0", "", "r1", "r2", "r3"), b.snapshot().map { it.text })
        b.feed("\u001B[2;1H\u001B[M") // geri sil
        assertEquals(listOf("r0", "r1", "r2", "r3"), b.snapshot().map { it.text })
    }

    @Test fun scrollRegionConfinesScrolling() {
        val b = TerminalBuffer(cols = 20, rows = 4)
        b.feed("a\r\nb\r\nc\r\nd")
        b.feed("\u001B[2;3r") // bölge: satır 2-3 (1-based)
        b.feed("\u001B[3;1H") // bölge dibine git (row idx 2)
        b.feed("\n\n") // bölge içinde 2 kez kaydır
        val lines = b.snapshot().map { it.text }
        assertEquals("a", lines[0]) // bölge dışı sabit
        assertEquals("d", lines[3]) // bölge dışı sabit
        assertEquals(0, b.scrollbackSize) // bölge içi kaydırma scrollback üretmez
    }

    @Test fun scrollbackOnlyFromMainScreenTop() {
        val b = TerminalBuffer(cols = 20, rows = 4)
        b.feed("l\r\n".repeat(10))
        assertTrue(b.scrollbackSize > 0)
        val before = b.scrollbackSize
        b.feed("\u001B[?1049h" + "x\r\n".repeat(10)) // alt ekranda kaydır
        assertEquals(before, b.scrollbackSize)
    }

    @Test fun eraseDisplayModes() {
        val b = TerminalBuffer(cols = 20, rows = 4)
        b.feed("a\r\nb\r\nc\r\nd")
        b.feed("\u001B[2;1H\u001B[1J") // baştan imlece sil
        assertEquals(listOf("", "", "c", "d"), b.snapshot().map { it.text })
        b.feed("\u001B[3J") // scrollback temizle
        assertEquals(0, b.scrollbackSize)
    }

    @Test fun resizeShrinkMovesTopToScrollback() {
        val b = TerminalBuffer(cols = 20, rows = 4)
        b.feed("r0\r\nr1\r\nr2\r\nr3")
        b.setScreenSize(20, 2)
        // snapshot = scrollback (r0,r1) + ekran (r2,r3)
        assertEquals(listOf("r0", "r1", "r2", "r3"), b.snapshot().map { it.text })
        assertEquals(2, b.scrollbackSize)
    }

    @Test fun escapeSplitAcrossChunks() {
        val b = TerminalBuffer()
        b.feed("a\u001B[3") // yarım CSI
        b.feed("2mgreen\r\n") // tamamlanıyor
        val line = b.snapshot().single()
        assertEquals("agreen", line.text)
        assertEquals(0xFF3FD68F, line.spans[1].style.fg)
    }

    @Test fun boundsAndCount() {
        val b = TerminalBuffer(maxLines = 10, rows = 4)
        b.feed("line\r\n".repeat(100))
        assertEquals(10, b.scrollbackSize) // scrollback tavanı
        assertEquals(10 + 3, b.snapshot().size) // + görünen ekran (3 dolu satır)
        b.clear()
        assertEquals(0, b.lineCount)
    }

    @Test fun burst10MbPerformance() {
        val b = TerminalBuffer()
        val chunk = buildString {
            repeat(1000) { append("\u001B[32mok\u001B[0m log satırı $it \u001B[31muyarı\u001B[0m\r\n") }
        }
        val t0 = System.nanoTime()
        while (b.totalFed < 10_000_000) b.feed(chunk)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("10MB burst ${b.totalFed}B işlendi (${ms}ms)", ms < 10_000)
        assertTrue(b.totalFed >= 10_000_000)
    }

    @Test fun osc52CopiesToClipboard() {
        val b = TerminalBuffer()
        var got: String? = null
        b.onClipboard = { got = it }
        b.feed("\u001B]52;c;aGVsbG8\u0007") // "hello" base64, BEL sonlandırma
        assertEquals("hello", got)
        got = null
        b.feed("\u001B]52;c;d29ybGQ=\u001B\\") // ST sonlandırma
        assertEquals("world", got)
        got = null
        b.feed("\u001B]52;c;?\u0007") // sorgu → callback yok
        assertNull(got)
    }

    @Test fun oscTitleReported() {
        val b = TerminalBuffer()
        var title = ""
        b.onTitle = { title = it }
        b.feed("\u001B]2;tmux: main\u0007")
        assertEquals("tmux: main", title)
        b.feed("\u001B]0;vim\u001B\\")
        assertEquals("vim", title)
    }

    @Test fun bracketedPasteModeTracked() {
        val b = TerminalBuffer()
        assertFalse(b.bracketedPaste)
        b.feed("\u001B[?2004h")
        assertTrue(b.bracketedPaste)
        b.feed("\u001B[?2004l")
        assertFalse(b.bracketedPaste)
    }

    @Test fun spacesAdvanceCursorPastTrimmedCells() {
        // "ls" + 3 boşluk: satır metni kırpılır ("ls") ama imleç sütun 5'te
        // kalmalı — render katmanı boşluk doldurur (TerminalRenderTest).
        val b = TerminalBuffer(cols = 20, rows = 4)
        b.feed("ls   ")
        assertEquals(0 to 5, b.cursorPosition())
        assertEquals("ls", b.snapshot()[0].text)
    }

    @Test fun bellFiresCallback() {
        var bells = 0
        val b = TerminalBuffer()
        b.onBell = { bells++ }
        b.feed("x\u0007y\u0007")
        assertEquals(2, bells)
    }

    @Test fun bellInsideOscDoesNotFire() {
        var bells = 0
        val b = TerminalBuffer()
        b.onBell = { bells++ }
        b.feed("\u001B]0;title\u0007") // OSC: BEL sonlandırıcı zil değildir
        assertEquals(0, bells)
    }

    @Test fun cursorPositionTracksScreen() {
        val b = TerminalBuffer(cols = 20, rows = 4)
        b.feed("ab\r\ncd")
        assertEquals(1 to 2, b.cursorPosition())
        b.feed("\u001B[?25l") // imleç gizle
        assertNull(b.cursorPosition())
    }

    @Test fun osc8Hyperlinks() {
        val b = TerminalBuffer()
        b.feed("\u001B]8;;https://example.com\u0007tıkla\u001B]8;;\u0007 düz\r\n")
        val line = b.snapshot().single()
        assertEquals("tıkla düz", line.text)
        assertEquals("https://example.com", line.spans[0].style.link)
        assertNull(line.spans[1].style.link)
    }
}
