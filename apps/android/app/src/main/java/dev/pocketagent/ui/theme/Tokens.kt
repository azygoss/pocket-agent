// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

// Tasarım tokenleri (docs/design.md). Ekranlar ham renk kullanmaz; her tema
// aynı rollere türetilir. Yüzeyler zeminden metne doğru tonla yükselir:
// bg → card → raised → overlay. Metin ve durum rolleri, üstünde durdukları
// yüzeye karşı ölçülen kontrastla üretilir — katalogdaki 19 temanın hepsi
// aynı okunurluk tabanını (metin ≥4.5:1, ikon/kenar ≥3:1) taşır.

// Bir anlamın dört hâli: nokta/ikon rengi (≥3:1), kart üstünde metin
// (≥4.5:1), yumuşak zemin ve o zemin üstündeki metin (≥4.5:1).
@Immutable
data class Tone(val fg: Color, val text: Color, val soft: Color, val onSoft: Color)

@Immutable
data class PocketTokens(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val raised: Color,
    val overlay: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryText: Color,
    val accent: Tone,
    val live: Tone,
    val busy: Tone,
    val warn: Tone,
    val danger: Tone,
    val neutral: Tone,
    // Durum taşımayan ayırt edici ton (görsel dosyalar gibi tür işaretleri).
    val violet: Tone,
    val avatars: List<Color>,
    val termBg: Color,
    val termFg: Color,
)

fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

// Rengi `toward` yönüne adım adım kaydırarak zemine karşı hedef kontrasta
// ulaştırır; zaten yeterliyse dokunmaz. Temanın kendi mürekkebi de yetmezse
// (ör. Solarized'ın kısık metni) beyaza/siyaha doğru devam eder.
fun Color.readableOn(bg: Color, toward: Color, min: Float = 4.5f): Color {
    if (contrast(this, bg) >= min) return this
    val extreme = if (contrast(Color.White, bg) >= contrast(Color.Black, bg)) Color.White else Color.Black
    for (target in listOf(toward, extreme)) {
        var t = 0.05f
        while (t <= 1f) {
            val c = lerp(this, target, t)
            if (contrast(c, bg) >= min) return c
            t += 0.05f
        }
    }
    return extreme
}

fun ConsoleTheme.tokens(): PocketTokens {
    val bg = Color(background)
    val ink = Color(text)
    // Açık temada ton basamakları daha kısa: gri-üstü-gri bulanıklaşmasın.
    val k = if (dark) 1f else 0.75f
    val card = lerp(bg, ink, 0.055f * k)
    val raised = lerp(bg, ink, 0.10f * k)
    val overlay = lerp(bg, ink, 0.16f * k)
    val accentC = Color(accent).readableOn(card, ink, 3f)
    val onPrimary = if (contrast(Color.Black, accentC) >= contrast(Color.White, accentC)) Color(0xFF06140E) else Color.White
    fun tone(c: Color, softMix: Float = if (dark) 0.17f else 0.13f): Tone {
        val fg = c.readableOn(card, ink, 3f)
        val soft = lerp(card, fg, softMix)
        return Tone(fg, fg.readableOn(raised, ink, 4.5f), soft, fg.readableOn(soft, ink, 4.5f))
    }
    val dimC = Color(dim)
    return PocketTokens(
        dark = dark,
        bg = bg,
        card = card,
        raised = raised,
        overlay = overlay,
        line = lerp(bg, ink, if (dark) 0.12f else 0.11f),
        lineStrong = lerp(bg, ink, 0.24f).readableOn(card, ink, 3f),
        text = ink,
        text2 = dimC.readableOn(raised, ink, 4.5f),
        text3 = dimC.readableOn(card, ink, 3f),
        primary = accentC,
        onPrimary = onPrimary,
        primaryText = accentC.readableOn(raised, ink, 4.5f),
        accent = tone(accentC),
        live = tone(Color(term.ansi[2])),
        busy = tone(Color(term.ansi[4])),
        warn = tone(Color(warning)),
        danger = tone(Color(error)),
        neutral = Tone(dimC.readableOn(card, ink, 3f), dimC.readableOn(raised, ink, 4.5f), raised, dimC.readableOn(raised, ink, 4.5f)),
        violet = tone(Color(term.ansi[13])),
        avatars = (9..14).map { Color(term.ansi[it]) },
        termBg = Color(term.background),
        termFg = Color(term.foreground),
    )
}

val LocalTokens = staticCompositionLocalOf { PocketConsoleTheme.tokens() }

// Kod gibi okunan değerler (host, port, yol, komut, parmak izi) IBM Plex
// Mono ile basılır; terminalin kendisi kullanıcının seçtiği fonttur.
val Code = IbmPlexMonoFamily

// Hareket tokenleri: girişler yavaşlayarak gelir, çıkışlar hızlanarak gider
// ve girişin ~%60'ı sürer. Animasyon kapalıysa (animator ölçeği 0) her şey
// anında son durumuna gelir.
object Motion {
    const val Fast = 120
    const val Standard = 220
    const val Emphasized = 320
    val Enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
