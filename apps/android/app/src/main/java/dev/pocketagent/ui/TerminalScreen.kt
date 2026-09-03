// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.TerminalController
import dev.pocketagent.transport.TerminalInput
import dev.pocketagent.transport.TerminalSize
import dev.pocketagent.transport.TransportFailure
import dev.pocketagent.ui.theme.TermAmber
import dev.pocketagent.ui.theme.TermBg
import dev.pocketagent.ui.theme.TermGreen
import dev.pocketagent.ui.theme.TermRed
import dev.pocketagent.ui.theme.TermText

@Composable
fun TerminalScreen(controller: TerminalController, settings: SettingsViewModel) {
    val vm = controller.vm
    val frames by vm.frames.collectAsState()
    val state by controller.state.collectAsState()
    val failure by controller.failure.collectAsState()
    val active by controller.connectedTo.collectAsState()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(frames.size) {
        if (frames.isNotEmpty()) listState.scrollToItem(frames.size - 1)
    }

    fun sendText(s: String) {
        controller.send(TerminalInput.Text(s))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Durum çubuğu
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StateDot(state)
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        when (state) {
                            ConnectionState.ACTIVE -> "${vm.badge} • ${active?.host}"
                            ConnectionState.CONNECTING -> "Bağlanıyor…"
                            ConnectionState.FAILED -> "Hata"
                            else -> "Bağlı değil"
                        },
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { vm.shrink(); controller.send(TerminalInput.Resize(vm.size)) }) { Text("A-") }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { vm.grow(); controller.send(TerminalInput.Resize(vm.size)) }) { Text("A+") }
            if (state == ConnectionState.ACTIVE) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { controller.disconnect() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Bağlantıyı kes", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Hata kartı
        if (state == ConnectionState.FAILED && failure != null) {
            Surface(
                color = TermRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Text(
                    failureText(failure!!),
                    color = TermRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        // Terminal yüzeyi
        Surface(
            color = TermBg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp)
                .semantics { contentDescription = "Terminal çıktısı" },
        ) {
            if (frames.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "$ bağlantı bekleniyor",
                        color = TermGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (14 * settings.theme.fontScale).sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Bağlantılar sekmesinden bir host seç. Oturum ölürse tmux re-attach yapılır, komut yeniden çalıştırılmaz.",
                        color = TermText.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        items(frames) { line ->
                            Text(
                                line.trimEnd('\n'),
                                color = TermText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = (13 * settings.theme.fontScale).sp,
                                lineHeight = (16 * settings.theme.fontScale).sp,
                            )
                        }
                    }
                }
            }
        }

        // Ekstra tuşlar
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExtraKey("Esc") { sendText("\u001B") }
            ExtraKey("Tab") { sendText("\t") }
            ExtraKey("^C") { sendText("\u0003") }
            ExtraKey("^D") { sendText("\u0004") }
            ExtraKey("^Z") { sendText("\u001A") }
            ExtraKey("↑") { sendText("\u001B[A") }
            ExtraKey("↓") { sendText("\u001B[B") }
            ExtraKey("→") { sendText("\u001B[C") }
            ExtraKey("←") { sendText("\u001B[D") }
            ExtraKey("|") { sendText("|") }
            ExtraKey("~") { sendText("~") }
            ExtraKey("/") { sendText("/") }
            ExtraKey("-") { sendText("-") }
        }

        // Giriş satırı
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Komut yaz…", fontFamily = FontFamily.Monospace) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Terminal girişi" },
                singleLine = true,
                enabled = state == ConnectionState.ACTIVE,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotEmpty()) { sendText(text + "\n"); text = "" }
                }),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { if (text.isNotEmpty()) { sendText(text + "\n"); text = "" } },
                enabled = state == ConnectionState.ACTIVE && text.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = "Gönder" },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (state == ConnectionState.ACTIVE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExtraKey(label: String, onTap: () -> Unit) {
    OutlinedButton(
        onClick = onTap,
        shape = RoundedCornerShape(8.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

private fun failureText(f: TransportFailure): String = when (f) {
    is TransportFailure.AuthFailed -> "Kimlik doğrulama reddedildi. Parolayı/anahtarı kontrol et — fallback yok."
    is TransportFailure.HostKeyChanged -> "HOST ANAHTARI DEĞİŞTİ. Olası MITM — bağlantı durduruldu. Revoke + yeniden eşle."
    is TransportFailure.Network -> "Ağ hatası: ${f.reason}"
    is TransportFailure.MissingServer -> "Sunucu bileşeni eksik: ${f.what}"
}

fun defaultTerminalSize(): TerminalSize = TerminalSize(80, 24)
