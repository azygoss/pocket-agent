// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.ui.theme.ConsoleFonts
import dev.pocketagent.ui.theme.ConsoleThemes
import dev.pocketagent.ui.theme.colorScheme
import dev.pocketagent.ui.theme.consoleFont
import dev.pocketagent.ui.theme.consoleTheme
import dev.pocketagent.ui.theme.contrast
import dev.pocketagent.ui.theme.tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Tema/font kataloğu bütünlüğü: her tema 16 ANSI rengi taşımalı, kimlikler
// benzersiz olmalı, bilinmeyen kimlik varsayılana düşmeli.
class ThemeCatalogTest {
    @Test fun catalogIsComprehensiveAndUnique() {
        assertTrue("en az 12 tema olmalı", ConsoleThemes.size >= 12)
        assertEquals(ConsoleThemes.size, ConsoleThemes.map { it.id }.toSet().size)
        ConsoleThemes.forEach { t ->
            assertEquals("${t.id} ANSI boyutu", 16, t.term.ansi.size)
            assertTrue("${t.id} adı boş", t.name.isNotBlank())
        }
        assertTrue("en az 5 font olmalı", ConsoleFonts.size >= 5)
    }

    @Test fun unknownIdsFallBackToDefaults() {
        assertEquals("pocket", consoleTheme("does-not-exist").id)
        assertEquals("jetbrains", consoleFont("does-not-exist").id)
    }

    @Test fun colorSchemeBuildsForEveryTheme() {
        // Türetilen şema her tema için hatasız üretilmeli (dark + light).
        ConsoleThemes.forEach { it.colorScheme() }
    }

    // Okunurluk tabanı her temada: metin ≥4.5:1 (kart ve sayfa yüzeylerinde),
    // durum işaretleri ≥3:1, birincil düğme etiketi ≥4.5:1.
    @Test fun tokensMeetContrastFloorInEveryTheme() {
        ConsoleThemes.forEach { th ->
            val t = th.tokens()
            fun floor(what: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, min: Float) {
                val c = contrast(fg, bg)
                assertTrue("${th.id} $what %.2f < $min".format(c), c >= min - 0.01f)
            }
            listOf(t.bg to "bg", t.card to "card", t.raised to "raised").forEach { (s, n) ->
                floor("text/$n", t.text, s, 4.5f)
                floor("text2/$n", t.text2, s, 4.5f)
            }
            floor("primaryText/card", t.primaryText, t.card, 4.5f)
            floor("onPrimary/primary", t.onPrimary, t.primary, 4.5f)
            listOf(t.accent, t.live, t.busy, t.warn, t.danger, t.neutral, t.violet).forEachIndexed { i, tone ->
                floor("tone$i.fg/card", tone.fg, t.card, 3f)
                floor("tone$i.text/card", tone.text, t.card, 4.5f)
                floor("tone$i.onSoft/soft", tone.onSoft, tone.soft, 4.5f)
            }
        }
    }
}
