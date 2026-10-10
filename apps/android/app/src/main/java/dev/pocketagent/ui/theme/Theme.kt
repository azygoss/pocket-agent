// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Renkler ConsoleThemes.kt + Tokens.kt, fontlar Fonts.kt içinde. Bu dosya
// ölçü, köşe ve yazı ölçeğini tutar (docs/design.md).

// 4/8 ritmi. Ekran kenar boşluğu 16dp, bölümler arası 28dp.
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 28.dp
    val x3 = 32.dp
}

// Köşeler büyüdükçe yüzey de büyür: tuş 10, çip/alan 14, kart 22, sayfa 28.
// Düğmeler tam yuvarlak hap.
val KeyShape = RoundedCornerShape(10.dp)
val FieldShape = RoundedCornerShape(14.dp)
val CardShape = RoundedCornerShape(22.dp)
val InnerShape = RoundedCornerShape(14.dp)
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val PillShape = RoundedCornerShape(percent = 50)

private val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Tek aile, net basamaklar: başlıklar SemiBold ve hafif sıkı, gövde 16/14,
// etiketler Medium. En küçük okunur metin 12sp.
private fun pocketTypography(f: FontFamily) = Typography(
    displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = f, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = f, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = f, fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

@Composable
fun PocketAgentTheme(
    theme: ConsoleTheme = PocketConsoleTheme,
    mono: FontFamily = JetBrainsMonoFamily,
    content: @Composable () -> Unit,
) {
    // Tema türetimi (tokenler + renk şeması) ve tipografi yalnız tema
    // değişince hesaplanır — üst seviye her yeniden kompozisyonda değil.
    val tokens = remember(theme) { theme.tokens() }
    val scheme = remember(theme) { theme.colorScheme() }
    val typography = remember { pocketTypography(PlexSansFamily) }
    CompositionLocalProvider(
        LocalConsoleTheme provides theme,
        LocalTokens provides tokens,
        LocalMonoFont provides mono,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = PocketShapes,
            typography = typography,
            content = content,
        )
    }
}
