// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.pocketagent.android.R

// Terminal / veri tipografisi için seçilebilir font aileleri. İlk üçü
// gömülü (OFL-1.1), son üçü sistem fontu. Seçim LocalMonoFont ile yayılır;
// başlık/label/veri bu fontu, düz metin sistem sans'ı kullanır.
val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val IbmPlexMonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
)

val SpaceMonoFamily = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

data class ConsoleFont(val id: String, val name: String, val family: FontFamily)

val ConsoleFonts: List<ConsoleFont> = listOf(
    ConsoleFont("jetbrains", "JetBrains Mono", JetBrainsMonoFamily),
    ConsoleFont("plex", "IBM Plex Mono", IbmPlexMonoFamily),
    ConsoleFont("space", "Space Mono", SpaceMonoFamily),
    ConsoleFont("system_mono", "Sistem Mono", FontFamily.Monospace),
    ConsoleFont("system_sans", "Sistem Sans", FontFamily.SansSerif),
    ConsoleFont("system_serif", "Sistem Serif", FontFamily.Serif),
)

fun consoleFont(id: String): ConsoleFont = ConsoleFonts.firstOrNull { it.id == id } ?: ConsoleFonts.first()

val LocalMonoFont = staticCompositionLocalOf { JetBrainsMonoFamily }
