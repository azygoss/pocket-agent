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
// ölçekleri ve tema uygulamasını tutar (docs/design.md).

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

// Köşeler: piksel 2, kontrol/tuş 6, sheet/kart 10, diyalog 16. Derinlik
// gölgeyle değil, yüzey basamağı + hairline border ile kurulur.
private val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

// Sheet geometrisi: chrome üstünde 8dp içeride, 10dp köşe, hairline kenar.
val SheetInset = 8.dp
val SheetShape = RoundedCornerShape(10.dp)
val PixelShape = RoundedCornerShape(2.dp)

// İki ses: düzyazı/başlık/kontrol sistem sans; okumalar Readout (Plex Mono)
// ile çağıran tarafta açıkça uygulanır. Bölüm etiketi = labelMedium:
// cümle düzeni, 12sp medium, loş — büyük harf ve tracking yok.
private fun pocketTypography() = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun PocketAgentTheme(
    theme: ConsoleTheme = PocketConsoleTheme,
    mono: FontFamily = JetBrainsMonoFamily,
    blink: BlinkClock? = null,
    content: @Composable () -> Unit,
) {
    // Tema türetimi (tokenler + 30 rollü şema) ve tipografi yalnız tema
    // değişince hesaplanır — üst seviye her yeniden kompozisyonda değil.
    val tokens = remember(theme) { theme.tokens() }
    val scheme = remember(theme) { theme.colorScheme() }
    val typography = remember { pocketTypography() }
    CompositionLocalProvider(
        LocalConsoleTheme provides theme,
        LocalTokens provides tokens,
        LocalMonoFont provides mono,
        LocalBlinkClock provides blink,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = PocketShapes,
            typography = typography,
            content = content,
        )
    }
}
