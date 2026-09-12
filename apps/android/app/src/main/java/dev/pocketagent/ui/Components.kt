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
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.TermAmber
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed
import dev.pocketagent.ui.theme.LocalMonoFont
import java.util.Locale

// ── Bileşen kütüphanesi — "status-line" dili ─────────────────────────────────
// Uygulama bir multiplexer companion'ı: chrome'u iyi bir TUI gibi konuşur.
// Kurallar: hairline çerçeveli paneller (tonal blob yok), seçim/vurgu
// "reverse video" (vurgu zemin + onPrimary metin), mono yalnız veri/etiket
// taşır, köşeler keskin (10/6/4dp), gölge yalnız overlay'lerde.

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

// Bölüm etiketi: "// LABEL" — kaynak-yorumu motifi, mono + geniş tracking.
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        "// ${text.uppercase(Locale("tr"))}",
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = LocalMonoFont.current,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.1f, androidx.compose.ui.unit.TextUnitType.Sp),
        ),
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
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleMono: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
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

// Boş durum: prompt motifi (❯ + imleç bloğu) + başlık + açıklama + aksiyon.
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
        Box(
            Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, tint.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                .background(tint.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
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

// Segmentli geçiş: hairline kap, seçili segment "reverse video" (accent
// zemin + onPrimary metin) — terminaldeki inverse-video geleneği.
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
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
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = LocalMonoFont.current),
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

// ── Butonlar: 6dp köşe, 44dp hedef, mono etiket ─────────────────────────────
// Birincil buton = "reverse video" blok (accent zemin + onPrimary) — bu
// uygulamada seçim/aksiyon vurgusunun imzası.

private val ButtonShape = RoundedCornerShape(6.dp)

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
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontFamily = LocalMonoFont.current)) {
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
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontFamily = LocalMonoFont.current)) {
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
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontFamily = LocalMonoFont.current)) {
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
