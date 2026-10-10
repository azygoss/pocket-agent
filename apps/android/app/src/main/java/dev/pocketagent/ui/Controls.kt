// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import dev.pocketagent.ui.theme.Code
import dev.pocketagent.ui.theme.FieldShape
import dev.pocketagent.ui.theme.Motion
import dev.pocketagent.ui.theme.PillShape
import dev.pocketagent.ui.theme.SheetShape
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.Tone
import kotlinx.coroutines.launch

// ── Form ve katman kontrolleri ──────────────────────────────────────────────
// Etiket her zaman alanın üstünde görünür (yer tutucu etiket değildir),
// hata ve yardım metni alanın hemen altında. Kısa seçimler alttan açılan
// sayfada, onaylar diyalogda, uzun formlar tam ekranda.

@Composable
fun PocketTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    textStyle: TextStyle? = null,
) {
    val t = Tok
    Column(modifier) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (error != null) t.danger.text else t.text2,
            )
            Spacer(Modifier.height(6.dp))
        }
        val base = textStyle ?: MaterialTheme.typography.bodyLarge
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
            enabled = enabled,
            placeholder = placeholder?.let { p ->
                { Text(p, style = if (mono) base.copy(fontFamily = Code) else base, color = t.text3, maxLines = 1) }
            },
            leadingIcon = leading,
            trailingIcon = trailing,
            isError = error != null,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = (if (mono) base.copy(fontFamily = Code, fontSize = base.fontSize * 0.94f) else base).copy(color = t.text),
            shape = FieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = wellColor,
                unfocusedContainerColor = wellColor,
                disabledContainerColor = wellColor,
                errorContainerColor = wellColor,
                focusedBorderColor = t.primary,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = t.danger.fg,
                cursorColor = t.primary,
                focusedLeadingIconColor = t.text2,
                unfocusedLeadingIconColor = t.text2,
                focusedTrailingIconColor = t.text2,
                unfocusedTrailingIconColor = t.text2,
            ),
        )
        val note = error ?: helper
        if (note != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) t.danger.text else t.text2,
            )
        }
    }
}

@Composable
fun PocketSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = Tok
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = t.primary,
            checkedThumbColor = t.onPrimary,
            checkedBorderColor = t.primary,
            uncheckedTrackColor = wellColor,
            uncheckedThumbColor = t.text2,
            uncheckedBorderColor = t.lineStrong,
        ),
    )
}

// Ayar satırı: başlık + açıklama solda, kontrol sağda; satırın tamamı dokunulur.
@Composable
fun SwitchRow(title: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = checked, role = Role.Switch, onClick = onToggle)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Tok.text)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Tok.text2)
            }
        }
        Spacer(Modifier.width(Space.md))
        PocketSwitch(checked = checked, onCheckedChange = null)
    }
}

data class Segment<T>(val value: T, val label: String)

// Segmentli seçim: hap ray üstünde seçili parça yumuşak nane zeminde.
@Composable
fun <T> SegmentedTabs(
    options: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tok
    val onBase = LocalSurface.current == Color.Unspecified || LocalSurface.current == t.bg
    Row(
        modifier
            .clip(PillShape)
            .background(if (onBase) t.card else wellColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val bg by animateColorAsState(if (isSelected) t.accent.soft else Color.Transparent, tween(Motion.Fast), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(bg)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option.value) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = Space.md, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (isSelected) t.accent.onSoft else t.text2,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── Diyalog ─────────────────────────────────────────────────────────────────

@Composable
fun PocketDialog(
    onDismiss: () -> Unit,
    title: String,
    confirm: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismiss: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
    iconTone: Tone? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val t = Tok
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = confirm,
        dismissButton = dismiss,
        modifier = modifier,
        icon = icon?.let { i -> { IconTile(i, tone = iconTone ?: t.accent, size = 48.dp) } },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = t.text) },
        text = content?.let { c ->
            { CompositionLocalProvider(LocalContentColor provides t.text2, LocalSurface provides t.card) { c() } }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = t.card,
        titleContentColor = t.text,
        textContentColor = t.text2,
    )
}

// Yıkıcı eylem onayı: ne olacağını söyler; onay düğmesi mercan.
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    PocketDialog(
        onDismiss = onDismiss,
        title = title,
        content = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirm = {
            GhostButton(confirmLabel, onClick = onConfirm, color = if (danger) Tok.danger.text else Tok.primaryText)
        },
        dismiss = { GhostButton("Vazgeç", onClick = onDismiss, color = Tok.text2) },
    )
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
    PocketDialog(
        onDismiss = onDismiss,
        title = "Oturum adı",
        content = {
            PocketTextField(
                name, { name = it },
                placeholder = connName,
                helper = "Boş bırakırsan bağlantı adı kullanılır.",
            )
        },
        confirm = { GhostButton("Kaydet", onClick = { onSave(name.trim().ifBlank { null }) }) },
        dismiss = { GhostButton("Vazgeç", onClick = onDismiss, color = Tok.text2) },
    )
}

// ── Alttan açılan sayfa ─────────────────────────────────────────────────────
// İçerik `dismiss` ile sayfayı animasyonla kapatıp sonra onDismiss'i çağırır.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val t = Tok
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        scope.launch { state.hide() }.invokeOnCompletion { if (!state.isVisible) onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = SheetShape,
        containerColor = t.card,
        contentColor = t.text,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = if (t.dark) 0.6f else 0.36f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(PillShape)
                    .background(t.lineStrong),
            )
        },
    ) {
      CompositionLocalProvider(LocalSurface provides t.card) {
        if (title != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.xxl, vertical = Space.sm)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = if (subtitleMono) MaterialTheme.typography.bodySmall.copy(fontFamily = Code) else MaterialTheme.typography.bodyMedium,
                        color = t.text2,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
        }
        Column { content(dismiss) }
        Spacer(Modifier.height(Space.lg))
      }
    }
}

// Sayfa eylem satırı: ikon + etiket (+ açıklama). Yıkıcı eylem mercan.
@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    sublabel: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val t = Tok
    val fg = when {
        !enabled -> t.text3
        danger -> t.danger.text
        else -> t.text
    }
    Row(
        Modifier
            .fillMaxWidth()
            .tap(if (enabled) onClick else null)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = Space.xxl, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger && enabled) t.danger.fg else if (enabled) t.text2 else t.text3, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.lg + 2.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = fg)
            if (sublabel != null) {
                Text(sublabel, style = MaterialTheme.typography.bodySmall, color = if (enabled) t.text2 else t.text3)
            }
        }
    }
}

// ── Tam ekran katman ────────────────────────────────────────────────────────
// Uzun formlar ve okuyucular (host düzenleyici, dosya/diff, transcript).

@Composable
fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Diyalog kendi penceresinde: sistem çubuğu ikonları temaya uysun
        // (açık temada beyaz üstüne beyaz ikon kalmasın).
        val view = LocalView.current
        val dark = Tok.dark
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = Tok.bg, contentColor = Tok.text) { content() }
    }
}

// ── Bildirim şeridi ─────────────────────────────────────────────────────────

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }

// Kısa, kendiliğinden kaybolan geri bildirim ("Yol kopyalandı").
@Composable
fun rememberNotify(): (String) -> Unit {
    val host = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    return remember(host, scope) {
        { msg: String ->
            host?.let { h ->
                scope.launch {
                    h.currentSnackbarData?.dismiss()
                    h.showSnackbar(msg)
                }
            }
            Unit
        }
    }
}

// Ters zeminli şerit: her temada içerikten belirgin ayrılır.
@Composable
fun PocketSnackbar(data: SnackbarData) {
    val t = Tok
    Row(
        Modifier
            .padding(horizontal = Space.lg, vertical = Space.sm)
            .clip(RoundedCornerShape(16.dp))
            .background(t.text)
            .padding(horizontal = Space.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = t.bg)
    }
}
