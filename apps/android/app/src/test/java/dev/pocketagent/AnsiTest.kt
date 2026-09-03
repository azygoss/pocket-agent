// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.Ansi
import org.junit.Assert.*
import org.junit.Test

class AnsiTest {
    @Test fun stripsColorCsi() {
        assertEquals("red", Ansi.strip("\u001B[31mred\u001B[0m"))
    }
    @Test fun stripsOscTitle() {
        assertEquals("x", Ansi.strip("\u001B]0;title\u0007x"))
        assertEquals("x", Ansi.strip("\u001B]0;title\u001B\\x"))
    }
    @Test fun normalizesCarriageReturns() {
        assertEquals("a\nb\nc", Ansi.strip("a\r\nb\rc"))
    }
    @Test fun stripsCursorMovementAndControls() {
        assertEquals("hello", Ansi.strip("he\u001B[2Cll\u0007o"))
    }
    @Test fun keepsPlainText() {
        assertEquals("$ whoami\nuser\n", Ansi.strip("$ whoami\nuser\n"))
    }
}
