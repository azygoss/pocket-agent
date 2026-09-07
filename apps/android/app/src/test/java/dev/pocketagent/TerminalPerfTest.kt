// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.TerminalBuffer
import org.junit.Assert.assertTrue
import org.junit.Test

// P17 perf: TerminalBuffer sıcak yol ölçümleri. Eşikler cömert (CI/VPS yüküne
// dayanıklı) ama regresyonu yakalayacak kadar dar — beklenenin ~3-5 katı.
class TerminalPerfTest {

    private fun chunk(): String {
        // Gerçekçi kabuk çıktısı: SGR stilli satırlar + tmux-vari çerçeve karakterleri
        val sb = StringBuilder()
        for (i in 0 until 100) {
            sb.append("\u001B[32muser@host\u001B[0m:\u001B[34m~/proj\u001B[0m$ ls -la ── satır ")
            sb.append(i)
            sb.append(" \u001B[1;31mhata\u001B[0m normal metin ░▒▓\r\n")
        }
        return sb.toString()
    }

    @Test fun feedThroughput1MB() {
        val b = TerminalBuffer(50_000)
        val c = chunk() // ~7KB
        val reps = (1_000_000 / c.length) + 1
        val t0 = System.nanoTime()
        repeat(reps) { b.feed(c) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("feed 1MB styled: ${ms}ms")
        assertTrue("1MB feed 3s'i aşmamalı (ölçülen: ${ms}ms)", ms < 3000)
    }

    @Test fun snapshotBuildFast() {
        val b = TerminalBuffer(50_000)
        val c = chunk()
        repeat(50) { b.feed(c) } // ~350KB içerik
        val t0 = System.nanoTime()
        repeat(100) { b.snapshot() }
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("100x snapshot: ${ms}ms")
        assertTrue("100 snapshot 2s'i aşmamalı (ölçülen: ${ms}ms)", ms < 2000)
    }

    @Test fun resizeHeavyReflow() {
        val b = TerminalBuffer(10_000)
        val c = chunk()
        repeat(20) { b.feed(c) }
        val t0 = System.nanoTime()
        for (cols in listOf(132, 80, 120, 40, 200, 80)) {
            b.setScreenSize(cols, 24)
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("6x resize reflow: ${ms}ms")
        assertTrue("resize reflow 2s'i aşmamalı (ölçülen: ${ms}ms)", ms < 2000)
    }
}
