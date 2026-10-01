// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.ui.theme.LocalTokens
import dev.pocketagent.ui.theme.PixelShape
import dev.pocketagent.ui.theme.PocketTokens
import dev.pocketagent.ui.theme.Readout
import dev.pocketagent.ui.theme.SheetShape
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.rememberBlinkClock
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke

// ── Bileşen kütüphanesi — Pocket tasarım dili (docs/design.md) ──────────────
// Renk sinyaldir, süs değil: mavi canlı/aktif, amber "sana ihtiyaç var",
// mercan hata. Seçim yalnız zeminle (active) gösterilir — kenar şeridi yok.
// Durum işaretleri nokta değil pikseldir. Okumalar Readout (Plex Mono) ile.

val Tok: PocketTokens
    @Composable @ReadOnlyComposable
    get() = LocalTokens.current

// ── Yüzeyler ────────────────────────────────────────────────────────────────

// Kart: surface zemini + hairline kenar, 10dp. Bir bölümün satırları tek
// kartta yaşar; satırlar arasına SoftDivider konur.
@Composable
fun ConsoleCard(
    modifier: Modifier = Modifier,
    padding: Dp = Space.lg,
    containerColor: Color = Tok.surface,
    borderColor: Color = Tok.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Kart kendi içerik rengini verir — renksiz Text asla siyah düşmez.
    CompositionLocalProvider(LocalContentColor provides Tok.text) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(SheetShape)
                .background(containerColor)
                .border(1.dp, borderColor, SheetShape)
                .padding(padding),
            content = content,
        )
    }
}

// İkon karosu: adımın/satırın türünü söyler; rengiyle durumunu. Varsayılan
// sessiz mürekkep — renk yalnız sinyal varsa verilir.
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color = Tok.text2,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    containerColor: Color = Tok.raised,
) {
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

// Kart başlığı: semibold sans başlık + sağda isteğe bağlı aksiyon.
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Tok.text)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

// Sayfa başlığı: sakin sans başlık + mono okuma satırı.
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, meta: String? = null) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Tok.text)
        if (meta != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout),
                color = Tok.muted,
                maxLines = 1,
            )
        }
    }
}

// Bölüm etiketi: cümle düzeni, 12sp medium, loş — büyük harf/tracking yok.
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Tok.muted, modifier = modifier)
}

// ── Pikseller ───────────────────────────────────────────────────────────────

enum class Signal { Idle, Running, Live, NeedsYou, Error }

fun ConnectionState.signal(): Signal = when (this) {
    ConnectionState.ACTIVE -> Signal.Live
    ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Signal.Running
    ConnectionState.SUSPENDED -> Signal.NeedsYou
    ConnectionState.FAILED -> Signal.Error
    ConnectionState.CLOSED -> Signal.Idle
}

// Durum pikseli (≤2dp köşe): içi boş mavi = çalışıyor (ortak 1 Hz saatle
// yanıp söner), dolu mavi = canlı, dolu amber = sana ihtiyaç var, dolu
// mercan = hata, içi boş loş = boşta. Renk tek başına anlam taşımasın diye
// içi boş/dolu biçim farkı da var.
@Composable
fun SignalPixel(signal: Signal, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val t = Tok
    // Yanıp sönme fazı çizim aşamasında okunur → tik başına yalnız redraw.
    val clock = if (signal == Signal.Running) rememberBlinkClock() else null
    val color = when (signal) {
        Signal.Idle -> t.muted
        Signal.Running, Signal.Live -> t.accent
        Signal.NeedsYou -> t.warning
        Signal.Error -> t.danger
    }
    val hollow = signal == Signal.Idle || signal == Signal.Running
    Box(
        modifier.size(size).drawBehind {
            val r = CornerRadius(2.dp.toPx())
            if (hollow) {
                val w = 1.5.dp.toPx()
                val on = clock?.on ?: true
                drawRoundRect(
                    color.copy(alpha = if (on) 1f else 0.25f),
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(this.size.width - w, this.size.height - w),
                    cornerRadius = r,
                    style = Stroke(width = w),
                )
            } else {
                drawRoundRect(color, cornerRadius = r)
            }
        },
    )
}

@Composable
fun StatusPixel(state: ConnectionState, modifier: Modifier = Modifier, size: Dp = 8.dp) =
    SignalPixel(state.signal(), modifier, size)

// Meşgul göstergesi: dönen çember yerine yanıp sönen içi boş piksel + okuma.
@Composable
fun BusyPixel(label: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = label ?: "Yükleniyor" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalPixel(Signal.Running, size = 10.dp)
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontFamily = Readout), color = Tok.muted)
        }
    }
}

// Host sigil'i: host kimliğinden türetilen, aynalı 3×3 piksel deseni —
// altı tondan birinde. Klasör/sunucu ikonunun yerini alır; aynı host her
// yerde aynı işareti taşır.
@Composable
fun HostSigil(seed: String, modifier: Modifier = Modifier, size: Dp = 36.dp, dim: Boolean = false) {
    val t = Tok
    val h = seed.fold(0x811C9DC5.toInt()) { acc, c -> (acc xor c.code) * 0x01000193 }
    val hue = t.sigils[Math.floorMod(h, t.sigils.size)].copy(alpha = if (dim) 0.45f else 1f)
    // 6 bit: sol sütun (aynası sağ) + orta sütun. En az 4 piksel dolu olsun.
    var bits = (h ushr 8) and 0x3F
    if (Integer.bitCount(bits) < 3) bits = bits or 0x12
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(t.raised),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.5f)) {
            val cell = this.size.width / 3f
            val gap = cell * 0.14f
            val r = CornerRadius(cell * 0.12f)
            for (row in 0..2) for (col in 0..2) {
                val src = if (col == 2) 0 else col
                if ((bits shr (row * 2 + src)) and 1 == 1) {
                    drawRoundRect(
                        hue,
                        topLeft = Offset(col * cell + gap / 2, row * cell + gap / 2),
                        size = Size(cell - gap, cell - gap),
                        cornerRadius = r,
                    )
                }
            }
        }
    }
}

// Hücre ölçer: doluluk oranı yatay piksel hücreleriyle (kullanım, indirme).
// Eşiği aşınca amber — "sana ihtiyaç var".
@Composable
fun CellMeter(fraction: Float, modifier: Modifier = Modifier, cells: Int = 20, warnAt: Float = 0.85f) {
    val t = Tok
    val f = fraction.coerceIn(0f, 1f)
    val filled = (f * cells).let { if (f > 0f && it < 1f) 1 else it.toInt() }
    val fill = if (f >= warnAt) t.warning else t.accent
    Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(cells) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(PixelShape)
                    .background(if (i < filled) fill else t.faint.copy(alpha = 0.6f)),
            )
        }
    }
}

// ── Ayırıcılar ──────────────────────────────────────────────────────────────

@Composable
fun ConsoleDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Tok.border))
}

@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Tok.border))
}

// ── Satırlar ────────────────────────────────────────────────────────────────

// Evrensel liste satırı: leading işaret + başlık + alt satır + trailing.
// Kutusuz; ayırma çağıranın SoftDivider'ındadır. Basınca zemin kalkar
// (ripple), sınırlar oynamaz.
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleMono: Boolean = false,
    subtitleMono: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.lg, vertical = Space.md),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                } else if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = 56.dp)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (titleMono) MaterialTheme.typography.bodyMedium.copy(fontFamily = Readout)
                else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Tok.text,
                maxLines = 1,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = if (subtitleMono) MaterialTheme.typography.bodySmall.copy(fontFamily = Readout)
                    else MaterialTheme.typography.bodySmall,
                    color = Tok.muted,
                    maxLines = 1,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        }
    }
}

// Okuma çipi (transport, kategori, durum): 4dp köşe, mono metin. tone
// verilirse sinyal renginde (zemin %14), yoksa nötr.
@Composable
fun TagPill(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tone: Color? = null,
) {
    val color = tone ?: Tok.text2
    Box(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (active) color.copy(alpha = 0.14f) else Tok.hover)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
            color = if (active) color else Tok.text2,
            maxLines = 1,
        )
    }
}

// Boş durum: sessiz ikon karosu + başlık + açıklama + isteğe bağlı aksiyon.
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tint: Color = Tok.text2,
    art: List<String>? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (art != null) {
            PixelIllustration(art, cell = 8.dp)
            Spacer(Modifier.height(Space.xl))
        } else {
            IconTile(icon, tint, size = 52.dp)
            Spacer(Modifier.height(Space.lg))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = Tok.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Tok.muted,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

// Tek başına duran satır karosu: surface + hairline, 10dp.
@Composable
fun TonalTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides Tok.text) {
        Row(
            modifier
                .fillMaxWidth()
                .clip(SheetShape)
                .background(Tok.surface)
                .border(1.dp, Tok.border, SheetShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = Space.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

data class Segment<T>(val value: T, val label: String)

// Segmentli geçiş: loş ray üstünde sheet zemini — seçim yalnız zeminle.
@Composable
fun <T> SegmentedControl(
    options: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tok
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(t.hover)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val bg by animateColorAsState(if (isSelected) t.active else Color.Transparent, tween(130), label = "segment-bg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(bg)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option.value) })
                    .defaultMinSize(minHeight = 36.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) t.text else t.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

// Ayar satırı: başlık + açıklama solda, kontrol sağda.
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Tok.text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tok.muted)
            }
        }
        Spacer(Modifier.width(Space.md))
        control()
    }
}

// ── Butonlar ────────────────────────────────────────────────────────────────
// Birincil: mürekkep dolgu (kâğıtta koyu, grafitte açık) — mavi sinyale
// saklanır. İkincil: hairline çerçeve. 8dp köşe, 44dp hedef.

private val ButtonShape = RoundedCornerShape(8.dp)

@Composable
fun ConsoleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val t = Tok
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = t.text,
            contentColor = t.bg,
            disabledContainerColor = t.active,
            disabledContentColor = t.muted,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
    }
}

@Composable
fun ConsoleOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val t = Tok
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        enabled = enabled,
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        border = BorderStroke(1.dp, if (enabled) t.borderStrong else t.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = t.text,
            disabledContentColor = t.muted,
        ),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
    }
}

@Composable
fun ConsoleTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.textButtonColors(contentColor = Tok.text),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
    }
}

// Oturum yeniden adlandırma: tek alan; boş kaydet → bağlantı adına döner.
@Composable
fun RenameSessionDialog(
    connName: String,
    initial: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial ?: "") }
    PocketAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Oturum adı") },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("Ad") },
                    placeholder = { Text(connName) },
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Boş bırakırsan bağlantı adı kullanılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tok.muted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifBlank { null }) }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

// "az önce / 5 dk önce / 3 sa önce / 2 g önce" — bağlantı kartlarında son
// bağlanma zamanı için.
fun relativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    if (ts <= 0) return "hiç"
    val d = (now - ts) / 1000
    return when {
        d < 60 -> "az önce"
        d < 3600 -> "${d / 60} dk önce"
        d < 86400 -> "${d / 3600} sa önce"
        else -> "${d / 86400} g önce"
    }
}
