// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.android.R

// Terminal tipografisi: JetBrains Mono 2.304 (OFL-1.1, lisans assets/licenses).
val TerminalFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

// ── Console paleti ─────────────────────────────────────────────────────────
// Tasarım dili: terminal estetiği — koyu lacivert-siyah zemin, tek yeşil vurgu,
// keskin köşeler, ince border'lar. Parlak/süslü renkler yok.
val TermBg = Color(0xFF07090D)       // terminal yüzeyi (en koyu)
val ConsoleBg = Color(0xFF0A0D13)    // uygulama zemini
val TermSurface = Color(0xFF10151D)  // kart zemini
val TermSurfaceHigh = Color(0xFF171E29) // yükseltilmiş zemin
val ConsoleBorder = Color(0xFF232C3A)
val TermText = Color(0xFFD9E1EA)
val ConsoleDim = Color(0xFF8592A3)
val TermGreen = Color(0xFF3FD68F)    // birincil vurgu
val TermBlue = Color(0xFF5FA8F5)
val TermAmber = Color(0xFFE5B567)
val TermRed = Color(0xFFE6676B)
val TermPurple = Color(0xFFB48CE8)

private val DarkColors = darkColorScheme(
    primary = TermGreen,
    onPrimary = Color(0xFF04150C),
    primaryContainer = Color(0xFF12301F),
    onPrimaryContainer = Color(0xFFBFEFD8),
    secondary = TermBlue,
    onSecondary = Color(0xFF06121F),
    secondaryContainer = Color(0xFF152A42),
    onSecondaryContainer = Color(0xFFC9E0FA),
    tertiary = TermPurple,
    background = ConsoleBg,
    onBackground = TermText,
    surface = ConsoleBg,
    onSurface = TermText,
    surfaceVariant = TermSurfaceHigh,
    onSurfaceVariant = ConsoleDim,
    surfaceContainer = TermSurface,
    error = TermRed,
    onError = Color(0xFF210607),
    outline = ConsoleBorder,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7A46),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2DC),
    onPrimaryContainer = Color(0xFF062315),
    secondary = Color(0xFF1A5FB4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E3F8),
    onSecondaryContainer = Color(0xFF0A1C33),
    tertiary = Color(0xFF6D3FB8),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF1B2129),
    surface = Color(0xFFF5F7FA),
    onSurface = Color(0xFF1B2129),
    surfaceVariant = Color(0xFFE4E9F0),
    onSurfaceVariant = Color(0xFF55606E),
    surfaceContainer = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Color(0xFFD2D9E2),
)

// Keskin console köşeleri: 4/6/8dp (M3 varsayılan yuvarlaklıkları değil).
private val ConsoleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

private val ConsoleTypography = Typography(
    titleLarge = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    titleMedium = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    titleSmall = TextStyle(fontFamily = TerminalFont, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = TerminalFont, fontSize = 11.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = TerminalFont, fontSize = 10.sp, letterSpacing = 0.4.sp),
)

@Composable
fun PocketAgentTheme(dark: Boolean = isSystemInDarkTheme(), amoled: Boolean = false, content: @Composable () -> Unit) {
    val colors = when {
        !dark -> LightColors
        amoled -> DarkColors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0A0E14),
            surfaceVariant = Color(0xFF131922),
        )
        else -> DarkColors
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = ConsoleShapes,
        typography = ConsoleTypography,
        content = content,
    )
}
