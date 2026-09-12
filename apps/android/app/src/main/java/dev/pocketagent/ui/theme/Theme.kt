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

// "Status-line" dili: enstrüman paneli hissi — keskin köşeler (kart 10,
// kontrol 6), hairline kurallar, ters-video vurgular. Blob köşeler yok.
private val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

// Tipografi: UI metni sistem sans; mono yalnız veri taşıyan yerlerde
// (hostname, oturum pill'i, tuş şeridi, kod) LocalMonoFont ile açıkça
// uygulanır. Başlıklar semibold — sıkı letter-spacing ile.
private fun pocketTypography() = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.5.sp, letterSpacing = 0.4.sp),
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
            shapes = PocketShapes,
            typography = pocketTypography(),
            content = content,
        )
    }
}
