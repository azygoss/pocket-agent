// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

// Pocket tasarım dili tokenleri (docs/design.md). Yüzeyler chrome → bg
// (sheet) → surface (kart, kompozer) → raised (popover) diye basamaklanır.
// Renk sinyaldir: accent canlı/aktif, warning "sana ihtiyaç var", danger
// hata/dur, success yalnız sonuç okumaları (exit 0, diff +). Geri kalan
// her şey mürekkep (text/text2/muted); faint yalnız dekoratif — asla metin.
@Immutable
data class PocketTokens(
    val dark: Boolean,
    val chrome: Color,
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val hover: Color,
    val active: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentHot: Color,
    val warning: Color,
    val danger: Color,
    val success: Color,
    val sigils: List<Color>,
    val termBg: Color,
    val termFg: Color,
)

fun ConsoleTheme.tokens(): PocketTokens {
    val ink = text
    return PocketTokens(
        dark = dark,
        chrome = if (dark) blend(background, 0xFF000000, if (background == 0xFF000000) 0f else 0.38f)
        else blend(background, 0xFF8E9096, 0.10f),
        bg = Color(background),
        surface = Color(surface),
        raised = Color(surfaceHigh),
        hover = blend(background, ink, if (dark) 0.05f else 0.04f),
        active = blend(background, ink, if (dark) 0.10f else 0.075f),
        border = blend(background, border, if (dark) 0.75f else 0.85f),
        borderStrong = Color(border),
        text = Color(text),
        text2 = blend(text, dim, 0.45f),
        muted = Color(dim),
        faint = blend(background, dim, 0.32f),
        accent = Color(accent),
        accentHot = Color(accentAlt),
        warning = Color(warning),
        danger = Color(error),
        success = Color(term.ansi[2]),
        sigils = (9..14).map { Color(term.ansi[it]) },
        termBg = Color(term.background),
        termFg = Color(term.foreground),
    )
}

val LocalTokens = staticCompositionLocalOf { PocketConsoleTheme.tokens() }

// İkinci ses: her okuma (saat, sayı, host, yol, oturum adı, tuş) IBM Plex
// Mono ile basılır — terminal fontu seçiminden bağımsız. Düzyazı, başlık ve
// kontroller sistem sans'ı kullanır.
val Readout = IbmPlexMonoFamily

// ── Ortak 1 Hz saat ───────────────────────────────────────────────────────
// Çalışan bir şeyi gösteren içi boş mavi piksel bu saatle yanıp söner — CSS
// döngüsü/sonsuz animasyon yok. Saat yalnız en az bir abone varken tıklar
// (hiçbir şey çalışmıyorsa uyur); azaltılmış hareket açıksa sabit kalır.
class BlinkClock {
    var on by mutableStateOf(true)
        internal set
    internal var users by mutableIntStateOf(0)
}

val LocalBlinkClock = staticCompositionLocalOf<BlinkClock?> { null }

// Saati süren tek yer: uygulama kökü. Test ve önizlemede saat yoktur →
// abone piksel sabit "açık" kalır.
@Composable
fun BlinkClockDriver(clock: BlinkClock) {
    val reduced = rememberReducedMotion()
    val running = clock.users > 0 && !reduced
    LaunchedEffect(running) {
        clock.on = true
        while (running) {
            delay(500)
            clock.on = !clock.on
        }
    }
}

@Composable
fun blinkPhase(): Boolean {
    val clock = LocalBlinkClock.current ?: return true
    DisposableEffect(clock) {
        clock.users++
        onDispose { clock.users-- }
    }
    return clock.on
}

// Sistem "animasyonları kaldır" (animator süre ölçeği 0) → tek seferlik
// hareketler de anında son durumuna gelir.
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
