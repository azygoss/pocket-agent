// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.graphics.Color
import dev.pocketagent.transport.TermLine
import dev.pocketagent.transport.TermStyle
import dev.pocketagent.ui.toAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

// Render katmanı: build() satır sonundaki boşluk hücrelerini kırpar; imleç
// metin sonunun ötesindeyse aradaki mesafe boşlukla doldurulmalı ki imleç
// gerçek sütununda çizilsin (echo'lanan boşluklar görünür olsun).
class TerminalRenderTest {
    private fun line(text: String) = TermLine(listOf(TermLine.Span(text, TermStyle())))

    @Test fun cursorPadsTrimmedTrailingSpaces() {
        // "ls" + 3 echo'lanan boşluk: imleç sütun 5'te, satır metni "ls".
        val s = line("ls").toAnnotatedString(cursorCol = 5, cursorBg = Color.Red)
        assertEquals("ls    ", s.text) // 3 dolgu + 1 imleç hücresi
    }

    @Test fun cursorAtLineEndAppendsSingleCell() {
        val s = line("ls").toAnnotatedString(cursorCol = 2, cursorBg = Color.Red)
        assertEquals("ls ", s.text)
    }

    @Test fun cursorInsideLineDoesNotPad() {
        val s = line("ls -la").toAnnotatedString(cursorCol = 1, cursorBg = Color.Red)
        assertEquals("ls -la", s.text)
    }

    @Test fun noCursorLeavesLineUntouched() {
        val s = line("ls").toAnnotatedString(cursorCol = -1, cursorBg = Color.Red)
        assertEquals("ls", s.text)
    }

    @Test fun hiddenCursorWhenNoBg() {
        val s = line("ls").toAnnotatedString(cursorCol = 5, cursorBg = Color.Unspecified)
        assertEquals("ls", s.text)
    }
}
