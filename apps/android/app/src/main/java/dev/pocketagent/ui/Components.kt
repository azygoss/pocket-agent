// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.TermAmber
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed
import dev.pocketagent.ui.theme.LocalMonoFont

// ── Bileşen kütüphanesi — flat editoryal dil ─────────────────────────────────
// İçerik düz zeminde yaşar: satırlar hairline ayırıcılarla ayrılır, kart
// yalnız gerçek bir panel olduğunda kullanılır. Accent tutumlu — birincil
// aksiyon, canlı durum ve seçili öğe dışında renk yok. Mono yalnız veri
// taşır (hostname, oturum adı, yol, komut).

// Panel: hairline border + hafif dolgu + 10dp köşe. Hiyerarşi border ve
// katman farkıyla kurulur; gölge kullanılmaz.
@Composable
fun ConsoleCard(
    modifier: Modifier = Modifier,
    padding: Dp = Space.lg,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (borderColor == Color.Transparent) null else BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(padding), content = content)
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
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

// Sayfa başlığı: büyük sakin başlık + isteğe bağlı mono meta satırı.
// Her ekran kendi başlığını taşır; üst bar ince bir şerit kalır.
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, meta: String? = null) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (meta != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalMonoFont.current),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// Bölüm etiketi: küçük, loş, geniş tracking. Metin verildiği gibi basılır
// (büyük harf dönüşümü çağıranın işi — test metinleri korunur).
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun StateDot(state: ConnectionState, size: Dp = 8.dp) {
    val color = when (state) {
        ConnectionState.ACTIVE -> TermGreen
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING, ConnectionState.SUSPENDED -> TermAmber
        ConnectionState.FAILED -> TermRed
        ConnectionState.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

// İnce ayırıcı çizgi (border rengi).
@Composable
fun ConsoleDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

// Daha yumuşak ayırıcı (liste içi).
@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

// Evrensel liste satırı: leading ikon/avatar + başlık + alt satır + trailing.
// Düz zeminde durur — kutu yok; ayırma işi çağıranın SoftDivider'ındadır.
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleMono: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.lg, vertical = Space.md),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 52.dp)
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
                style = if (titleMono) MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalMonoFont.current)
                else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

// Mikro etiket (transport, kategori, durum): hairline kapsül + mono metin.
// Aktifte accent border + accent metin — dolgusuz, enstrüman rozeti gibi.
@Composable
fun TagPill(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tone: Color? = null,
) {
    val color = tone ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .border(
                1.dp,
                if (active) color else MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalMonoFont.current),
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Boş durum: tek ikon + başlık + açıklama + isteğe bağlı aksiyon. Kutu yok.
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.8f),
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.height(Space.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

data class Segment<T>(val value: T, val label: String)

// Segmentli geçiş: tonal kap, seçili segment dolu accent.
@Composable
fun <T> SegmentedControl(
    options: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val bg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "segment-bg",
            )
            val fg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "segment-fg",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(bg)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option.value) })
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                )
            }
        }
    }
}

// Ayar satırı: başlık + açıklama solda, kontrol sağda; üstte ince ayırıcı.
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
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Space.md))
        control()
    }
}

// ── Butonlar: 8dp köşe, 44dp hedef, sans etiket ─────────────────────────────

private val ButtonShape = RoundedCornerShape(8.dp)

@Composable
fun ConsoleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            content()
        }
    }
}

@Composable
fun ConsoleOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            content()
        }
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
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            content()
        }
    }
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
