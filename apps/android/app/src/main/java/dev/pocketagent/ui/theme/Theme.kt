// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Terminal estetiği: koyu tarafta koyu lacivert zemin + mavi/yeşil vurgu.
val TermBg = Color(0xFF0D1117)
val TermSurface = Color(0xFF161B22)
val TermSurfaceHigh = Color(0xFF21262D)
val TermBlue = Color(0xFF58A6FF)
val TermGreen = Color(0xFF3FB950)
val TermAmber = Color(0xFFD29922)
val TermRed = Color(0xFFF85149)
val TermText = Color(0xFFC9D1D9)
val TermPurple = Color(0xFFBB9AF7)

private val DarkColors = darkColorScheme(
    primary = TermBlue,
    onPrimary = Color(0xFF0B1526),
    primaryContainer = Color(0xFF1F3A5F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = TermGreen,
    onSecondary = Color(0xFF07130B),
    secondaryContainer = Color(0xFF17351F),
    onSecondaryContainer = Color(0xFFC9F2D6),
    tertiary = TermPurple,
    background = TermBg,
    onBackground = TermText,
    surface = TermBg,
    onSurface = TermText,
    surfaceVariant = TermSurfaceHigh,
    onSurfaceVariant = Color(0xFF9AA7B4),
    surfaceContainer = TermSurface,
    error = TermRed,
    onError = Color(0xFF2B0705),
    outline = Color(0xFF30363D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0B1526),
    secondary = Color(0xFF1A7F37),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F2D6),
    onSecondaryContainer = Color(0xFF07130B),
    tertiary = Color(0xFF8250DF),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFF6F8FA),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFE6EAF0),
    onSurfaceVariant = Color(0xFF57606A),
    surfaceContainer = Color.White,
    error = Color(0xFFCF222E),
    onError = Color.White,
    outline = Color(0xFFD0D7DE),
)

@Composable
fun PocketAgentTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
