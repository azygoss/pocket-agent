// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Renkler ve tipografi ConsoleThemes.kt / Fonts.kt içinde tanımlı. Bu dosya
// yalnız ölçekleri ve tema uygulamasını tutar.

// ── Ölçü tokenleri ─────────────────────────────────────────────────────────
// Tüm ekranlar bu ölçeği kullanır; ad-hoc 10/14/16 karışıklığı yok.
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

// Console köşeleri: yumuşak ama keskin karakter — 4/8/10/14dp.
private val ConsoleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

// Tipografi: veri/başlık/label = seçili mono; düz metin = sistem sans.
// Karışım kasıtlı: terminal kimliği korunur, uzun paragraflar okunaklı kalır.
private fun consoleTypography(mono: FontFamily) = Typography(
    headlineSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    titleSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = mono, fontSize = 11.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = mono, fontSize = 10.sp, letterSpacing = 0.4.sp),
)

@Composable
fun PocketAgentTheme(
    theme: ConsoleTheme = PocketConsoleTheme,
    mono: FontFamily = JetBrainsMonoFamily,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalConsoleTheme provides theme,
        LocalMonoFont provides mono,
    ) {
        MaterialTheme(
            colorScheme = theme.colorScheme(),
            shapes = ConsoleShapes,
            typography = consoleTypography(mono),
            content = content,
        )
    }
}
