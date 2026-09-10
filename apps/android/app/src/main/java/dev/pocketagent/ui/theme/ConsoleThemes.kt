// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Terminal renk seti: zemin/metin/imleç + ANSI-16. ANSI dizisi, SGR renkleri
// tema değişince canlı yeniden çözülsün diye UI katmanına taşınır (buffer
// yalnız indeksi saklar, rengi render anında bu paletten alır).
class TermPalette(
    val background: Long,
    val foreground: Long,
    val cursor: Long,
    val selection: Long,
    val ansi: LongArray,
)

data class ConsoleTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val background: Long,
    val surface: Long,
    val surfaceHigh: Long,
    val border: Long,
    val text: Long,
    val dim: Long,
    val accent: Long,
    val accentAlt: Long,
    val error: Long,
    val warning: Long,
    val term: TermPalette,
)

// Seçili tema tüm ağaca buradan yayılır (terminal ANSI + zemin dahil).
val LocalConsoleTheme = staticCompositionLocalOf { PocketConsoleTheme }

private fun a16(
    black: Long, red: Long, green: Long, yellow: Long, blue: Long, magenta: Long, cyan: Long, white: Long,
    bBlack: Long, bRed: Long, bGreen: Long, bYellow: Long, bBlue: Long, bMagenta: Long, bCyan: Long, bWhite: Long,
) = longArrayOf(black, red, green, yellow, blue, magenta, cyan, white, bBlack, bRed, bGreen, bYellow, bBlue, bMagenta, bCyan, bWhite)

private fun theme(
    id: String, name: String, dark: Boolean,
    background: Long, surface: Long, surfaceHigh: Long, border: Long,
    text: Long, dim: Long, accent: Long, accentAlt: Long, error: Long, warning: Long,
    termBg: Long, termFg: Long, cursor: Long, ansi: LongArray,
) = ConsoleTheme(
    id = id, name = name, dark = dark,
    background = background, surface = surface, surfaceHigh = surfaceHigh, border = border,
    text = text, dim = dim, accent = accent, accentAlt = accentAlt, error = error, warning = warning,
    term = TermPalette(termBg, termFg, cursor, selection = blend(background, accent, 0.30f).value.toLong(), ansi = ansi),
)

// ── Katalog ────────────────────────────────────────────────────────────────

val PocketConsoleTheme = theme(
    "pocket", "Pocket Console", true,
    background = 0xFF06080C, surface = 0xFF10141C, surfaceHigh = 0xFF161C26, border = 0xFF242D3A,
    text = 0xFFE4EAF2, dim = 0xFF8A95A7, accent = 0xFF41D892, accentAlt = 0xFF63A9F5, error = 0xFFE86A6E, warning = 0xFFE7B76C,
    termBg = 0xFF06080C, termFg = 0xFFD9E1EA, cursor = 0xFF41D892,
    ansi = a16(
        0xFF3A4552, 0xFFE6676B, 0xFF3FD68F, 0xFFE5B567, 0xFF5FA8F5, 0xFFB48CE8, 0xFF4FD0C5, 0xFFC6D0DC,
        0xFF5C6B7E, 0xFFF08589, 0xFF6FE3AC, 0xFFEECA8A, 0xFF83BCF7, 0xFFC9A6EF, 0xFF7ADED5, 0xFFFFFFFF,
    ),
)

val PocketAmoledTheme = theme(
    "pocket_amoled", "Pocket AMOLED", true,
    background = 0xFF000000, surface = 0xFF090B0F, surfaceHigh = 0xFF12161D, border = 0xFF232A35,
    text = 0xFFE4EAF2, dim = 0xFF8A95A7, accent = 0xFF41D892, accentAlt = 0xFF63A9F5, error = 0xFFE86A6E, warning = 0xFFE7B76C,
    termBg = 0xFF000000, termFg = 0xFFD9E1EA, cursor = 0xFF41D892,
    ansi = PocketConsoleTheme.term.ansi,
)

val ClaudeDarkTheme = theme(
    "claude_dark", "Claude Dark", true,
    background = 0xFF1A1915, surface = 0xFF24231F, surfaceHigh = 0xFF2E2C28, border = 0xFF3D3A34,
    text = 0xFFF0EEE6, dim = 0xFFA9A498, accent = 0xFFCC785C, accentAlt = 0xFFD4A27F, error = 0xFFE06C5A, warning = 0xFFD9A85C,
    termBg = 0xFF1A1915, termFg = 0xFFF0EEE6, cursor = 0xFFD4A27F,
    ansi = a16(
        0xFF2E2C28, 0xFFE06C5A, 0xFF8FA98E, 0xFFD9A85C, 0xFF7F9CC0, 0xFFC08FB0, 0xFF7FB0A8, 0xFFE6E2D6,
        0xFF6B675E, 0xFFF0857A, 0xFFA8C2A6, 0xFFEDC57E, 0xFF9DB8D8, 0xFFD8AECC, 0xFF9ECFC6, 0xFFFFFCF2,
    ),
)

val ClaudeLightTheme = theme(
    "claude_light", "Claude Light", false,
    background = 0xFFF0EEE6, surface = 0xFFFAF9F5, surfaceHigh = 0xFFE8E5DA, border = 0xFFDAD5C7,
    text = 0xFF2B2A26, dim = 0xFF6B675E, accent = 0xFFCC785C, accentAlt = 0xFFA65E45, error = 0xFFC0392B, warning = 0xFF9A6B18,
    termBg = 0xFFFAF9F5, termFg = 0xFF2B2A26, cursor = 0xFFCC785C,
    ansi = a16(
        0xFF2B2A26, 0xFFC0392B, 0xFF4F7A3E, 0xFF9A6B18, 0xFF3B6EA5, 0xFF9B5A8A, 0xFF2E7D74, 0xFFE0DCCE,
        0xFF6B675E, 0xFFA93226, 0xFF3F6B32, 0xFF7E5713, 0xFF2F5C8A, 0xFF7F4A71, 0xFF256A62, 0xFF1E1D1A,
    ),
)

val CodexDarkTheme = theme(
    "codex_dark", "Codex Dark", true,
    background = 0xFF0D0D0D, surface = 0xFF171717, surfaceHigh = 0xFF1F1F1F, border = 0xFF2E2E2E,
    text = 0xFFECECEC, dim = 0xFF9A9A9A, accent = 0xFF10A37F, accentAlt = 0xFF6EE7B7, error = 0xFFF87171, warning = 0xFFFBBF24,
    termBg = 0xFF0D0D0D, termFg = 0xFFECECEC, cursor = 0xFF10A37F,
    ansi = a16(
        0xFF2E2E2E, 0xFFF87171, 0xFF10A37F, 0xFFFBBF24, 0xFF60A5FA, 0xFFA78BFA, 0xFF22D3EE, 0xFFD4D4D4,
        0xFF6B6B6B, 0xFFFCA5A5, 0xFF34D399, 0xFFFCD34D, 0xFF93C5FD, 0xFFC4B5FD, 0xFF67E8F9, 0xFFFFFFFF,
    ),
)

val GithubDarkTheme = theme(
    "github_dark", "GitHub Dark", true,
    background = 0xFF0D1117, surface = 0xFF161B22, surfaceHigh = 0xFF21262D, border = 0xFF30363D,
    text = 0xFFC9D1D9, dim = 0xFF8B949E, accent = 0xFF58A6FF, accentAlt = 0xFF3FB950, error = 0xFFF85149, warning = 0xFFD29922,
    termBg = 0xFF0D1117, termFg = 0xFFC9D1D9, cursor = 0xFF58A6FF,
    ansi = a16(
        0xFF484F58, 0xFFFF7B72, 0xFF3FB950, 0xFFD29922, 0xFF58A6FF, 0xFFBC8CFF, 0xFF39C5CF, 0xFFB1BAC4,
        0xFF6E7681, 0xFFFFA198, 0xFF56D364, 0xFFE3B341, 0xFF79C0FF, 0xFFD2A8FF, 0xFF56D4DD, 0xFFF0F6FC,
    ),
)

val GithubLightTheme = theme(
    "github_light", "GitHub Light", false,
    background = 0xFFFFFFFF, surface = 0xFFF6F8FA, surfaceHigh = 0xFFEAEEF2, border = 0xFFD0D7DE,
    text = 0xFF1F2328, dim = 0xFF59636E, accent = 0xFF0969DA, accentAlt = 0xFF1A7F37, error = 0xFFCF222E, warning = 0xFF9A6700,
    termBg = 0xFFFFFFFF, termFg = 0xFF1F2328, cursor = 0xFF0969DA,
    ansi = a16(
        0xFF24292F, 0xFFCF222E, 0xFF116329, 0xFF4D2D00, 0xFF0969DA, 0xFF8250DF, 0xFF1B7C83, 0xFF6E7781,
        0xFF57606A, 0xFFA40E26, 0xFF1A7F37, 0xFF633C01, 0xFF218BFF, 0xFFA475F9, 0xFF3192AA, 0xFF8C959F,
    ),
)

val NotionLightTheme = theme(
    "notion_light", "Notion Light", false,
    background = 0xFFFFFFFF, surface = 0xFFF7F7F5, surfaceHigh = 0xFFEFEFED, border = 0xFFE3E2E0,
    text = 0xFF37352F, dim = 0xFF787774, accent = 0xFF2383E2, accentAlt = 0xFF0F7B6C, error = 0xFFEB5757, warning = 0xFFDFAB01,
    termBg = 0xFFFFFFFF, termFg = 0xFF37352F, cursor = 0xFF2383E2,
    ansi = a16(
        0xFF37352F, 0xFFEB5757, 0xFF0F7B6C, 0xFFDFAB01, 0xFF2383E2, 0xFF9A6DD7, 0xFF0B7285, 0xFFE3E2E0,
        0xFF787774, 0xFFD64B4B, 0xFF0C6559, 0xFFB98A00, 0xFF1B6FBF, 0xFF8259BF, 0xFF085E6E, 0xFF191919,
    ),
)

val NotionDarkTheme = theme(
    "notion_dark", "Notion Dark", true,
    background = 0xFF191919, surface = 0xFF202020, surfaceHigh = 0xFF2A2A2A, border = 0xFF333333,
    text = 0xFFE6E6E4, dim = 0xFF9B9B9B, accent = 0xFF529CCA, accentAlt = 0xFF4DAB9A, error = 0xFFEB5757, warning = 0xFFDFAB01,
    termBg = 0xFF191919, termFg = 0xFFE6E6E4, cursor = 0xFF529CCA,
    ansi = a16(
        0xFF2A2A2A, 0xFFEB5757, 0xFF4DAB9A, 0xFFDFAB01, 0xFF529CCA, 0xFF9A6DD7, 0xFF3AA8B8, 0xFFD4D4D4,
        0xFF6B6B6B, 0xFFF07A7A, 0xFF63C4B3, 0xFFEEC13A, 0xFF6FB0D8, 0xFFB18BE0, 0xFF55C1D0, 0xFFFFFFFF,
    ),
)

val DraculaTheme = theme(
    "dracula", "Dracula", true,
    background = 0xFF282A36, surface = 0xFF2E3140, surfaceHigh = 0xFF343746, border = 0xFF44475A,
    text = 0xFFF8F8F2, dim = 0xFF8A93B8, accent = 0xFFBD93F9, accentAlt = 0xFF8BE9FD, error = 0xFFFF5555, warning = 0xFFF1FA8C,
    termBg = 0xFF282A36, termFg = 0xFFF8F8F2, cursor = 0xFFF8F8F2,
    ansi = a16(
        0xFF21222C, 0xFFFF5555, 0xFF50FA7B, 0xFFF1FA8C, 0xFFBD93F9, 0xFFFF79C6, 0xFF8BE9FD, 0xFFF8F8F2,
        0xFF6272A4, 0xFFFF6E6E, 0xFF69FF94, 0xFFFFFFA5, 0xFFD6ACFF, 0xFFFF92DF, 0xFFA4FFFF, 0xFFFFFFFF,
    ),
)

val NordTheme = theme(
    "nord", "Nord", true,
    background = 0xFF2E3440, surface = 0xFF3B4252, surfaceHigh = 0xFF434C5E, border = 0xFF4C566A,
    text = 0xFFECEFF4, dim = 0xFF9AA5B5, accent = 0xFF88C0D0, accentAlt = 0xFF81A1C1, error = 0xFFBF616A, warning = 0xFFEBCB8B,
    termBg = 0xFF2E3440, termFg = 0xFFD8DEE9, cursor = 0xFF88C0D0,
    ansi = a16(
        0xFF3B4252, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B, 0xFF81A1C1, 0xFFB48EAD, 0xFF88C0D0, 0xFFE5E9F0,
        0xFF4C566A, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B, 0xFF81A1C1, 0xFFB48EAD, 0xFF8FBCBB, 0xFFECEFF4,
    ),
)

val GruvboxTheme = theme(
    "gruvbox", "Gruvbox Dark", true,
    background = 0xFF282828, surface = 0xFF32302F, surfaceHigh = 0xFF3C3836, border = 0xFF504945,
    text = 0xFFEBDBB2, dim = 0xFFA89984, accent = 0xFFB8BB26, accentAlt = 0xFF83A598, error = 0xFFFB4934, warning = 0xFFFABD2F,
    termBg = 0xFF282828, termFg = 0xFFEBDBB2, cursor = 0xFFEBDBB2,
    ansi = a16(
        0xFF282828, 0xFFCC241D, 0xFF98971A, 0xFFD79921, 0xFF458588, 0xFFB16286, 0xFF689D6A, 0xFFA89984,
        0xFF928374, 0xFFFB4934, 0xFFB8BB26, 0xFFFABD2F, 0xFF83A598, 0xFFD3869B, 0xFF8EC07C, 0xFFEBDBB2,
    ),
)

val OneDarkTheme = theme(
    "one_dark", "One Dark", true,
    background = 0xFF282C34, surface = 0xFF2C313A, surfaceHigh = 0xFF333842, border = 0xFF3E4451,
    text = 0xFFABB2BF, dim = 0xFF5C6370, accent = 0xFF61AFEF, accentAlt = 0xFF98C379, error = 0xFFE06C75, warning = 0xFFE5C07B,
    termBg = 0xFF282C34, termFg = 0xFFABB2BF, cursor = 0xFF528BFF,
    ansi = a16(
        0xFF282C34, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B, 0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFABB2BF,
        0xFF5C6370, 0xFFE06C75, 0xFF98C379, 0xFFE5C07B, 0xFF61AFEF, 0xFFC678DD, 0xFF56B6C2, 0xFFFFFFFF,
    ),
)

val TokyoNightTheme = theme(
    "tokyo_night", "Tokyo Night", true,
    background = 0xFF1A1B26, surface = 0xFF1F2335, surfaceHigh = 0xFF24283B, border = 0xFF292E42,
    text = 0xFFC0CAF5, dim = 0xFF565F89, accent = 0xFF7AA2F7, accentAlt = 0xFFBB9AF7, error = 0xFFF7768E, warning = 0xFFE0AF68,
    termBg = 0xFF1A1B26, termFg = 0xFFC0CAF5, cursor = 0xFF7AA2F7,
    ansi = a16(
        0xFF15161E, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFA9B1D6,
        0xFF414868, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68, 0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFC0CAF5,
    ),
)

val CatppuccinTheme = theme(
    "catppuccin_mocha", "Catppuccin Mocha", true,
    background = 0xFF1E1E2E, surface = 0xFF181825, surfaceHigh = 0xFF313244, border = 0xFF45475A,
    text = 0xFFCDD6F4, dim = 0xFFA6ADC8, accent = 0xFF89B4FA, accentAlt = 0xFFCBA6F7, error = 0xFFF38BA8, warning = 0xFFF9E2AF,
    termBg = 0xFF1E1E2E, termFg = 0xFFCDD6F4, cursor = 0xFFF5E0DC,
    ansi = a16(
        0xFF45475A, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF, 0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFBAC2DE,
        0xFF585B70, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF, 0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFA6ADC8,
    ),
)

val SolarizedDarkTheme = theme(
    "solarized_dark", "Solarized Dark", true,
    background = 0xFF002B36, surface = 0xFF073642, surfaceHigh = 0xFF0A3F4C, border = 0xFF15414A,
    text = 0xFF93A1A1, dim = 0xFF657B83, accent = 0xFF268BD2, accentAlt = 0xFF2AA198, error = 0xFFDC322F, warning = 0xFFB58900,
    termBg = 0xFF002B36, termFg = 0xFF839496, cursor = 0xFF93A1A1,
    ansi = a16(
        0xFF073642, 0xFFDC322F, 0xFF859900, 0xFFB58900, 0xFF268BD2, 0xFFD33682, 0xFF2AA198, 0xFFEEE8D5,
        0xFF002B36, 0xFFCB4B16, 0xFF586E75, 0xFF657B83, 0xFF839496, 0xFF6C71C4, 0xFF93A1A1, 0xFFFDF6E3,
    ),
)

val SolarizedLightTheme = theme(
    "solarized_light", "Solarized Light", false,
    background = 0xFFFDF6E3, surface = 0xFFEEE8D5, surfaceHigh = 0xFFE4DDC8, border = 0xFFD3CBB7,
    text = 0xFF586E75, dim = 0xFF93A1A1, accent = 0xFF268BD2, accentAlt = 0xFF2AA198, error = 0xFFDC322F, warning = 0xFFB58900,
    termBg = 0xFFFDF6E3, termFg = 0xFF657B83, cursor = 0xFF586E75,
    ansi = a16(
        0xFF073642, 0xFFDC322F, 0xFF859900, 0xFFB58900, 0xFF268BD2, 0xFFD33682, 0xFF2AA198, 0xFFEEE8D5,
        0xFF002B36, 0xFFCB4B16, 0xFF586E75, 0xFF657B83, 0xFF839496, 0xFF6C71C4, 0xFF93A1A1, 0xFFFDF6E3,
    ),
)

val MonokaiTheme = theme(
    "monokai", "Monokai", true,
    background = 0xFF272822, surface = 0xFF2E2F28, surfaceHigh = 0xFF383830, border = 0xFF49483E,
    text = 0xFFF8F8F2, dim = 0xFFA6A28C, accent = 0xFFA6E22E, accentAlt = 0xFF66D9EF, error = 0xFFF92672, warning = 0xFFE6DB74,
    termBg = 0xFF272822, termFg = 0xFFF8F8F2, cursor = 0xFFF8F8F0,
    ansi = a16(
        0xFF272822, 0xFFF92672, 0xFFA6E22E, 0xFFE6DB74, 0xFF66D9EF, 0xFFAE81FF, 0xFFA1EFE4, 0xFFF8F8F2,
        0xFF75715E, 0xFFF92672, 0xFFA6E22E, 0xFFE6DB74, 0xFF66D9EF, 0xFFAE81FF, 0xFFA1EFE4, 0xFFF9F8F5,
    ),
)

val ConsoleThemes: List<ConsoleTheme> = listOf(
    PocketConsoleTheme, PocketAmoledTheme,
    ClaudeDarkTheme, ClaudeLightTheme,
    CodexDarkTheme,
    GithubDarkTheme, GithubLightTheme,
    NotionLightTheme, NotionDarkTheme,
    DraculaTheme, NordTheme, GruvboxTheme, OneDarkTheme, TokyoNightTheme, CatppuccinTheme,
    SolarizedDarkTheme, SolarizedLightTheme, MonokaiTheme,
)

fun consoleTheme(id: String): ConsoleTheme = ConsoleThemes.firstOrNull { it.id == id } ?: PocketConsoleTheme

// ── Material renk şeması türetimi ───────────────────────────────────────────
// Her tema yalnız temel renkleri verir; kapsayıcı/outline tonları burada
// karıştırılarak üretilir. Böylece 18 tema için şema yazmak gerekmez.

internal fun blend(a: Long, b: Long, t: Float): Color {
    val ca = Color(a); val cb = Color(b)
    return Color(
        red = ca.red + (cb.red - ca.red) * t,
        green = ca.green + (cb.green - ca.green) * t,
        blue = ca.blue + (cb.blue - ca.blue) * t,
        alpha = 1f,
    )
}

private fun onColor(c: Long): Color =
    if (Color(c).luminance() > 0.5f) Color(0xFF0B0D10) else Color(0xFFF8FAFC)

fun ConsoleTheme.colorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(accent),
        onPrimary = onColor(accent),
        primaryContainer = blend(background, accent, 0.24f),
        onPrimaryContainer = Color(text),
        secondary = Color(accentAlt),
        onSecondary = onColor(accentAlt),
        secondaryContainer = blend(background, accentAlt, 0.22f),
        onSecondaryContainer = Color(text),
        tertiary = Color(accentAlt),
        onTertiary = onColor(accentAlt),
        background = Color(background),
        onBackground = Color(text),
        surface = Color(background),
        onSurface = Color(text),
        surfaceVariant = Color(surfaceHigh),
        onSurfaceVariant = Color(dim),
        surfaceContainer = Color(surface),
        surfaceContainerHigh = Color(surfaceHigh),
        surfaceContainerLow = blend(background, surface, 0.55f),
        surfaceContainerLowest = Color(background),
        error = Color(error),
        onError = onColor(error),
        outline = Color(border),
        outlineVariant = blend(background, border, 0.55f),
    )
}
