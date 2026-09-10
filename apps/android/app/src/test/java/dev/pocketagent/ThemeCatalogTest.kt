// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.ui.theme.ConsoleFonts
import dev.pocketagent.ui.theme.ConsoleThemes
import dev.pocketagent.ui.theme.colorScheme
import dev.pocketagent.ui.theme.consoleFont
import dev.pocketagent.ui.theme.consoleTheme
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
}
