// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.TerminalController
import dev.pocketagent.ui.theme.CardShape
import dev.pocketagent.ui.theme.Code
import dev.pocketagent.ui.theme.InnerShape
import dev.pocketagent.ui.theme.LocalTokens
import dev.pocketagent.ui.theme.PillShape
import dev.pocketagent.ui.theme.PocketTokens
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.Tone
import dev.pocketagent.ui.theme.readableOn
import dev.pocketagent.ui.theme.rememberReducedMotion

// ── Bileşen kütüphanesi (docs/design.md) ────────────────────────────────────
// Ton katmanlı yüzeyler (çizgi yok, gölge yok), büyük köşeler, hap düğmeler.
// Durum her yerde renk + metin + biçimle söylenir; renk tek ipucu değildir.

val Tok: PocketTokens
    @Composable @ReadOnlyComposable
    get() = LocalTokens.current

// Üstünde durulan yüzey (zemin, kart, sayfa). Alan, segment rayı ve pasif
// düğme gibi "gömük" kontroller bir ton yukarı çıkar ki her yüzeyde görünsün.
val LocalSurface = staticCompositionLocalOf { Color.Unspecified }

val wellColor: Color
    @Composable @ReadOnlyComposable
    get() = if (LocalSurface.current == Tok.raised) Tok.overlay else Tok.raised

// Tıklama + uzun basma tek yerden: uzun basma varsa combinedClickable.
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tap(onClick: (() -> Unit)?, onLongClick: (() -> Unit)? = null, label: String? = null): Modifier = when {
    onLongClick != null -> combinedClickable(onClickLabel = label, onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    onClick != null -> clickable(onClickLabel = label, onClick = onClick)
    else -> this
}

// ── Durum ───────────────────────────────────────────────────────────────────

enum class Status { Live, Busy, Warn, Error, Idle }

fun ConnectionState.status(): Status = when (this) {
    ConnectionState.ACTIVE -> Status.Live
    ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Status.Busy
    ConnectionState.SUSPENDED -> Status.Warn
    ConnectionState.FAILED -> Status.Error
    ConnectionState.CLOSED -> Status.Idle
}

// Bağlantı durumunun insan dili — rozet, alt satır ve sekmede aynı kelimeler.
fun ConnectionState.label(retry: Int = 0): String = if (retry > 0) {
    "Yeniden bağlanıyor $retry/${TerminalController.MAX_RETRY}"
} else when (this) {
    ConnectionState.ACTIVE -> "Bağlı"
    ConnectionState.CONNECTING -> "Bağlanıyor"
    ConnectionState.RECONNECTING -> "Yeniden bağlanıyor"
    ConnectionState.SUSPENDED -> "Askıda"
    ConnectionState.FAILED -> "Bağlantı hatası"
    ConnectionState.CLOSED -> "Bağlantı kapalı"
}

@Composable
@ReadOnlyComposable
fun Status.tone(): Tone = when (this) {
    Status.Live -> Tok.live
    Status.Busy -> Tok.busy
    Status.Warn -> Tok.warn
    Status.Error -> Tok.danger
    Status.Idle -> Tok.neutral
}

// Durum noktası: dolu = bir durum var, halka = boşta. Bağlanırken yumuşakça
// nabız atar (animasyon kapalıysa sabit); nabız çizim katmanında okunur.
@Composable
fun StatusDot(status: Status, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val tone = status.tone()
    val reduced = rememberReducedMotion()
    val pulse = if (status == Status.Busy && !reduced) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulse-a",
        )
    } else {
        null
    }
    Box(
        modifier
            .size(size)
            .graphicsLayer { alpha = pulse?.value ?: 1f }
            .then(
                if (status == Status.Idle) Modifier.border(1.5.dp, tone.fg, CircleShape)
                else Modifier.background(tone.fg, CircleShape),
            ),
    )
}

// Durum rozeti: yumuşak zeminde nokta + kelime.
@Composable
fun StatusChip(status: Status, label: String, modifier: Modifier = Modifier) {
    val tone = status.tone()
    Row(
        modifier
            .clip(PillShape)
            .background(tone.soft)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status, size = 7.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tone.onSoft, maxLines = 1)
    }
}

// Küçük etiket: transport, kaynak, "başka cihaz" gibi okumalar.
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, tone: Tone? = null, mono: Boolean = false) {
    val bg = tone?.soft ?: wellColor
    val fg = tone?.onSoft ?: Tok.text2
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.let { if (mono) it.copy(fontFamily = Code, fontWeight = FontWeight.Normal) else it },
        color = fg,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ── Kimlik ──────────────────────────────────────────────────────────────────

// Host avatarı: adın ilk harfi, host+kullanıcıdan türetilen tonda yuvarlak
// kare. Aynı makine her ekranda aynı renkle görünür.
@Composable
fun HostAvatar(name: String, seed: String, modifier: Modifier = Modifier, size: Dp = 40.dp, dim: Boolean = false) {
    val t = Tok
    val (bg, fg) = remember(seed, t) {
        val h = seed.fold(0x811C9DC5.toInt()) { acc, c -> (acc xor c.code) * 0x01000193 }
        val hue = t.avatars[Math.floorMod(h, t.avatars.size)]
        val b = lerp(t.card, hue, if (t.dark) 0.24f else 0.16f)
        b to hue.readableOn(b, t.text, 4.5f)
    }
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase(java.util.Locale.forLanguageTag("tr")) ?: "?"
    Box(
        modifier
            .size(size)
            .graphicsLayer { alpha = if (dim) 0.5f else 1f }
            .clip(RoundedCornerShape(size * 0.32f))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.42f).sp, lineHeight = (size.value * 0.5f).sp),
            color = fg,
        )
    }
}

// Tür ikonu karosu: dosya türü, ayar kategorisi, araç adımı.
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tone: Tone? = null, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(tone?.soft ?: wellColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone?.fg ?: Tok.text2, modifier = Modifier.size(size * 0.5f))
    }
}

// ── Yüzeyler ────────────────────────────────────────────────────────────────

// Kart: zeminden bir ton yukarıda, 22dp köşe. Kenar çizgisi ve gölge yok.
@Composable
fun PocketCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    color: Color = Tok.card,
    shape: Shape = CardShape,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides Tok.text, LocalSurface provides color) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color)
                .tap(onClick, onLongClick)
                .padding(contentPadding),
            content = content,
        )
    }
}

// Bölüm başlığı: sayfa içindeki gruplar. Ekran okuyucuya başlık olarak bildirilir.
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            color = Tok.text,
            modifier = Modifier.semantics { heading() },
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count, style = MaterialTheme.typography.labelMedium, color = Tok.text2)
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun InsetDivider(start: Dp = 0.dp) {
    HorizontalDivider(Modifier.padding(start = start), thickness = 1.dp, color = Tok.line)
}

// Evrensel satır: sol işaret + başlık + alt satır + sağ öğe. En az 56dp.
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleMono: Boolean = false,
    subtitleMono: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    titleColor: Color = Tok.text,
    subtitleMaxLines: Int = 1,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.lg, vertical = Space.md),
) {
    Row(
        modifier
            .fillMaxWidth()
            .tap(onClick, onLongClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Space.md + 2.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (titleMono) MaterialTheme.typography.bodyMedium.copy(fontFamily = Code)
                else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = if (subtitleMono) MaterialTheme.typography.bodySmall.copy(fontFamily = Code)
                    else MaterialTheme.typography.bodyMedium,
                    color = Tok.text2,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        }
    }
}

// ── Düğmeler ────────────────────────────────────────────────────────────────
// Birincil: dolu nane hap, ekranda bir tane. Tonlu: yumuşak nane zemin,
// ikincil eylem. Çerçeveli: nötr alternatif. Metin: satır içi eylem.

private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    val t = Tok
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = PillShape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) t.danger.fg else t.primary,
            contentColor = if (danger) (if (t.dark) Color(0xFF1A0605) else Color.White) else t.onPrimary,
            disabledContainerColor = wellColor,
            disabledContentColor = t.text3,
        ),
    ) { ButtonContent(text, icon) }
}

@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val t = Tok
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = PillShape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = t.accent.soft,
            contentColor = t.accent.onSoft,
            disabledContainerColor = wellColor,
            disabledContentColor = t.text3,
        ),
    ) { ButtonContent(text, icon) }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val t = Tok
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = PillShape,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, if (enabled) t.lineStrong else t.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = t.text, disabledContentColor = t.text3),
    ) { ButtonContent(text, icon) }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Tok.primaryText,
    icon: ImageVector? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = PillShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = Tok.text3),
    ) { ButtonContent(text, icon) }
}

// 48dp dokunma hedefli ikon eylemi; açıklama ekran okuyucuya gider.
@Composable
fun IconAction(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Tok.text2,
    selected: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (selected) Tok.accent.soft else Color.Transparent,
            contentColor = if (selected) Tok.accent.onSoft else tint,
            disabledContentColor = Tok.text3,
        ),
    ) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(22.dp))
    }
}

// ── Üst çubuk ───────────────────────────────────────────────────────────────

// Ekran başlığı: geri + başlık (+ isteğe bağlı alt satır) + eylemler.
@Composable
fun PocketTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    backDesc: String = "Geri",
    insetTop: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (insetTop) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .heightIn(min = 64.dp)
            .padding(start = if (onBack != null) 4.dp else Space.lg, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconAction(backIcon, backDesc, onBack, tint = Tok.text)
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Tok.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                CompositionLocalProvider(LocalContentColor provides Tok.text2) { subtitle() }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// ── Geri bildirim ───────────────────────────────────────────────────────────

// Boş durum: tonlu ikon karosu + başlık + açıklama + eylemler.
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xxl, vertical = Space.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(icon, tone = Tok.accent, size = 64.dp)
        Spacer(Modifier.height(Space.lg + 4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Tok.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Tok.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(Space.xl))
            action()
        }
    }
}

// Satır içi uyarı: durum tonunda yumuşak kutu, ikon + başlık + açıklama + eylem.
@Composable
fun Banner(
    status: Status,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val tone = status.tone()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(tone.soft)
            .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = if (actions != null) 2.dp else Space.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tone.onSoft, modifier = Modifier.padding(top = 1.dp).size(20.dp))
                Spacer(Modifier.width(Space.md))
            } else {
                StatusDot(status, Modifier.padding(top = 6.dp))
                Spacer(Modifier.width(Space.md))
            }
            Column(Modifier.weight(1f).padding(end = Space.sm)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Tok.text)
                if (body != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = Tok.text2)
                }
            }
        }
        if (actions != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

// Kopyalanabilir komut satırı: "$" istemi + mono komut + kopyala.
@Composable
fun CodeLine(code: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(wellColor)
            .padding(start = Space.lg, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Code), color = Tok.primaryText)
        Spacer(Modifier.width(10.dp))
        Text(
            code,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Code),
            color = Tok.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconAction(Icons.Rounded.ContentCopy, "Kopyala", onCopy)
    }
}

// Mono değer kutusu: parmak izi, public anahtar gibi okunacak/kopyalanacak veri.
@Composable
fun ValueBox(text: String, modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(wellColor)
            .padding(horizontal = Space.md + 2.dp, vertical = Space.md),
    ) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Tok.text2)
            Spacer(Modifier.height(4.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code), color = Tok.text)
    }
}

// İnce ilerleme çubuğu: kullanım ve indirme. Eşiği geçince amber.
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, warnAt: Float = 0.85f) {
    val f = fraction.coerceIn(0f, 1f)
    val fill = if (f >= warnAt) Tok.warn.fg else Tok.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(PillShape)
            .background(wellColor)
            .semantics { contentDescription = "%${(f * 100).toInt()}" },
    ) {
        Box(Modifier.fillMaxWidth(f).height(8.dp).clip(PillShape).background(fill))
    }
}

// "az önce / 5 dk önce / 3 sa önce / 2 g önce" — son bağlanma, olay zamanı.
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
