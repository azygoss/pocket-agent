// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.ui.ImeEdit
import dev.pocketagent.ui.imeEdit
import org.junit.Assert.assertEquals
import org.junit.Test

// Görünmez IME alanının PTY girdisine çevrilmesi: ham terminal hissi için
// her tuş vuruşu tek delta olmalı; düşen/tekrarlanan karakter olmamalı.
class TerminalInputTest {
    @Test fun appendInsertsSuffix() {
        assertEquals(ImeEdit.Insert("a"), imeEdit("", "a"))
        assertEquals(ImeEdit.Insert("bc"), imeEdit("a", "abc"))
    }

    @Test fun backspaceDeletesTail() {
        assertEquals(ImeEdit.Delete(1), imeEdit("ab", "a"))
        assertEquals(ImeEdit.Delete(2), imeEdit("abc", "a"))
    }

    @Test fun pasteInsertsWholeChunk() {
        assertEquals(ImeEdit.Insert("ls -la\n"), imeEdit("", "ls -la\n"))
    }

    @Test fun noChangeIsNoop() {
        assertEquals(ImeEdit.None, imeEdit("x", "x"))
        assertEquals(ImeEdit.None, imeEdit("", ""))
    }

    @Test fun sameLengthReplacementSendsChangedTail() {
        assertEquals(ImeEdit.Insert("xc"), imeEdit("abc", "axc"))
    }
}
