// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pocketagent.ui.theme.Readout

// ── Pocket kontrolleri ──────────────────────────────────────────────────────
// Material bileşenlerinin tasarım diline giydirilmiş halleri. Ekranlar
// material3.AlertDialog / OutlinedTextField / Switch yerine bunları kullanır
// (aynı imza — çağrı yerleri değişmeden dil tek yerden uygulanır).

// Diyalog: raised zemin + hairline, 16dp köşe, gölgesiz. İçerideki metin
// düğmeleri mürekkep rengindedir (mavi sinyale saklı); tehlikeli eylem
// kendi mercan rengini açıkça verir.
@Composable
fun PocketAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val t = Tok
    val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.86f
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = t.text, onPrimary = t.bg)) {
            CompositionLocalProvider(LocalContentColor provides t.text) {
                Column(
                    modifier
                        .padding(horizontal = 20.dp)
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxH)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(t.raised)
                        .border(1.dp, t.border, MaterialTheme.shapes.extraLarge)
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                ) {
                    if (icon != null) {
                        icon()
                        Spacer(Modifier.height(14.dp))
                    }
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(color = t.text)) { title() }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (text != null) {
                        Box(Modifier.weight(1f, fill = false)) {
                            CompositionLocalProvider(LocalContentColor provides t.text2) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

// Metin alanı: etiket kutunun ÜSTÜNDE görünür kalır (yer tutucu etiket
// değildir), kutu sheet zemininde hairline; odakta 1dp mavi. mono=true →
// değer Readout ile (host, port, URL, token, yol, desen).
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    textStyle: TextStyle? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    colors: TextFieldColors? = null,
    mono: Boolean = false,
) {
    val t = Tok
    Column(modifier) {
        if (label != null) {
            CompositionLocalProvider(LocalContentColor provides if (isError) t.danger else t.text2) {
                ProvideTextStyle(MaterialTheme.typography.labelMedium) { label() }
            }
            Spacer(Modifier.height(6.dp))
        }
        val base = textStyle ?: MaterialTheme.typography.bodyMedium
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = placeholder?.let { p ->
                { CompositionLocalProvider(LocalContentColor provides t.muted) { p() } }
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            textStyle = (if (mono) base.copy(fontFamily = Readout) else base).copy(color = t.text),
            shape = shape,
            colors = colors ?: OutlinedTextFieldDefaults.colors(
                focusedContainerColor = t.bg,
                unfocusedContainerColor = t.bg,
                disabledContainerColor = t.hover,
                focusedBorderColor = t.accent,
                unfocusedBorderColor = t.borderStrong,
                disabledBorderColor = t.border,
                errorBorderColor = t.danger,
                cursorColor = t.accent,
                focusedLeadingIconColor = t.text2,
                unfocusedLeadingIconColor = t.muted,
                focusedPlaceholderColor = t.muted,
                unfocusedPlaceholderColor = t.muted,
            ),
        )
    }
}

// Switch: açıkken mavi ray (aktif = sinyal), kapalıyken loş ray + hairline.
@Composable
fun Switch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = Tok
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = t.accent,
            checkedThumbColor = Color.White,
            checkedBorderColor = t.accent,
            uncheckedTrackColor = t.active,
            uncheckedThumbColor = t.muted,
            uncheckedBorderColor = t.borderStrong,
        ),
    )
}

// Snackbar: raised + hairline, canlı piksel + metin. Gölgesiz.
@Composable
fun PocketSnackbar(data: SnackbarData) {
    val t = Tok
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(t.raised)
            .border(1.dp, t.borderStrong, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalPixel(Signal.Live, size = 7.dp)
        Spacer(Modifier.size(10.dp))
        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = t.text)
    }
}

// ── Piksel illüstrasyonları ─────────────────────────────────────────────────
// Boş durumlar ikon yerine küçük piksel çizimleri taşır — işaretle aynı dil.
// Harf → renk: a accent, w warning, d danger, i text2, m muted, f faint.

object PixelArt {
    val Terminal = listOf(
        "fffffffffffff",
        "f...........f",
        "f.a.........f",
        "f..a........f",
        "f...a.......f",
        "f..a........f",
        "f.a...iiw...f",
        "f...........f",
        "fffffffffffff",
    )
    val Folder = listOf(
        "ffff.........",
        "f...f........",
        "ffffffffffff.",
        "f..........f.",
        "f...a......f.",
        "f....a.....f.",
        "f...a..ii..f.",
        "f..........f.",
        "ffffffffffff.",
    )
    val Hosts = listOf(
        "aaa...iii...m",
        "a.a...i.i...m",
        "aaa...iii...m",
        ".............",
        "iii...mmm...w",
        "i.i...m.m...w",
        "iii...mmm...w",
    )
}

@Composable
fun PixelIllustration(rows: List<String>, modifier: Modifier = Modifier, cell: Dp = 7.dp, desc: String? = null) {
    val t = Tok
    val cols = rows.maxOf { it.length }
    Canvas(
        modifier
            .size(cell * cols, cell * rows.size)
            .then(if (desc != null) Modifier.semantics { contentDescription = desc } else Modifier),
    ) {
        val c = size.width / cols
        val gap = c * 0.16f
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { col, ch ->
                val color = when (ch) {
                    'a' -> t.accent
                    'w' -> t.warning
                    'd' -> t.danger
                    'i' -> t.text2
                    'm' -> t.muted
                    'f' -> t.faint
                    else -> null
                } ?: return@forEachIndexed
                drawRoundRect(
                    color,
                    topLeft = Offset(col * c + gap / 2, r * c + gap / 2),
                    size = Size(c - gap, c - gap),
                    cornerRadius = CornerRadius(c * 0.14f),
                )
            }
        }
    }
}
