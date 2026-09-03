// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.TerminalBuffer
import org.junit.Assert.*
import org.junit.Test

class TerminalBufferTest {
    @Test fun plainLinesAndPartialLine() {
        val b = TerminalBuffer()
        b.feed("hello")
        assertEquals("hello", b.snapshot().single().text)
        b.feed(" world\nnext\n")
        assertEquals(listOf("hello world", "next"), b.snapshot().map { it.text })
    }

    @Test fun carriageReturnOverwrites() {
        val b = TerminalBuffer()
        b.feed("progress 10%\rprogress 90%\n")
        assertEquals("progress 90%", b.snapshot().single().text)
    }

    @Test fun backspaceEdits() {
        val b = TerminalBuffer()
        b.feed("abc\b\bXY")
        assertEquals("aXY", b.snapshot().single().text)
    }

    @Test fun sgrColorsBecomeSpans() {
        val b = TerminalBuffer()
        b.feed("\u001B[31mred\u001B[0m plain\n")
        val line = b.snapshot().single()
        assertEquals("red plain", line.text)
        assertEquals(2, line.spans.size)
        assertEquals(0xFFF85149, line.spans[0].style.fg)
        assertNull(line.spans[1].style.fg)
    }

    @Test fun boldAndBrightAnd256() {
        val b = TerminalBuffer()
        b.feed("\u001B[1;96mhi\u001B[0m\n")
        val s = b.snapshot().single().spans.single().style
        assertTrue(s.bold)
        assertEquals(0xFF56D4DD, s.fg) // bright cyan
        val b2 = TerminalBuffer()
        b2.feed("\u001B[38;5;196mx\n")
        assertEquals(0xFFFF0000, b2.snapshot().single().spans.single().style.fg) // kırmızı küp
    }

    @Test fun eraseLineAndCursorMoves() {
        val b = TerminalBuffer()
        b.feed("abcdef\u001B[3DZZ") // 3 geri, üstüne yaz
        assertEquals("abcZZf", b.snapshot().single().text)
        val b2 = TerminalBuffer()
        b2.feed("junk\r\u001B[Kclean\n")
        assertEquals("clean", b2.snapshot().single().text)
    }

    @Test fun escapeSplitAcrossChunks() {
        val b = TerminalBuffer()
        b.feed("a\u001B[3") // yarım CSI
        b.feed("2mgreen\n") // tamamlanıyor
        val line = b.snapshot().single()
        assertEquals("agreen", line.text)
        assertEquals(0xFF3FB950, line.spans[1].style.fg)
    }

    @Test fun boundsAndCount() {
        val b = TerminalBuffer(maxLines = 10)
        b.feed("line\n".repeat(100))
        assertEquals(10, b.lineCount)
        assertEquals(10, b.snapshot().size)
        b.clear()
        assertEquals(0, b.lineCount)
    }

    @Test fun burst10MbPerformance() {
        val b = TerminalBuffer()
        val chunk = buildString {
            repeat(1000) { append("\u001B[32mok\u001B[0m log satırı $it \u001B[31muyarı\u001B[0m\n") }
        }
        val t0 = System.nanoTime()
        while (b.totalFed < 10_000_000) b.feed(chunk)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("10MB burst ${b.totalFed}B işlendi (${ms}ms)", ms < 10_000)
        assertTrue(b.totalFed >= 10_000_000)
    }
}
