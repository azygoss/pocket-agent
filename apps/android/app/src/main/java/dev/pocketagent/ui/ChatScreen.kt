// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pocketagent.net.ChatBlockDto
import dev.pocketagent.ui.theme.Readout
import dev.pocketagent.ui.theme.SheetShape
import dev.pocketagent.ui.theme.rememberReducedMotion

// P14: agent transcript görünümü. Bloklar gateway /chat üzerinden gelir
// (tam içerik yalnız SSH tünelinde; backend yalnız ≤256 karakter özet görür).
// Sunum (docs/design.md): agent işi yumuşak bir adım listesidir — balon
// yok, kenar şeridi yok. Her araç çağrısı kenarsız bir satır: türünü
// söyleyen ikon karosu, sonucu rengiyle (mercan = hata, sessiz = bitti).
// Art arda üç+ araç tek bir grup satırına katlanır.
@Composable
fun ChatDialog(title: String, blocks: List<ChatBlockDto>, onClose: () -> Unit) {
    val t = Tok
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
                .clip(SheetShape)
                .background(t.bg)
                .border(1.dp, t.border, SheetShape),
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("›", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Readout), color = t.muted)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = Readout),
                    color = t.text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = t.text2)
                }
            }
            ConsoleDivider()
            if (blocks.isEmpty()) {
                Text(
                    "Blok yok — transcript boş ya da tanınmadı.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.muted,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                ) {
                    items(foldSteps(blocks)) { item ->
                        when (item) {
                            is ChatItem.Single -> ChatBlockRow(item.block)
                            is ChatItem.Group -> ToolGroupRow(item.blocks)
                        }
                    }
                }
            }
        }
    }
}

private sealed interface ChatItem {
    data class Single(val block: ChatBlockDto) : ChatItem
    data class Group(val blocks: List<ChatBlockDto>) : ChatItem
}

private fun ChatBlockDto.isStep() = role == "tool" || role == "result" || role == "error"

// Art arda 3+ araç/sonuç bloğu → tek grup satırı (açılınca adımlar).
private fun foldSteps(blocks: List<ChatBlockDto>): List<ChatItem> {
    val out = mutableListOf<ChatItem>()
    var run = mutableListOf<ChatBlockDto>()
    fun flush() {
        if (run.count { it.role == "tool" } >= 3) out += ChatItem.Group(run)
        else run.forEach { out += ChatItem.Single(it) }
        run = mutableListOf()
    }
    for (b in blocks) {
        if (b.isStep()) run += b else { flush(); out += ChatItem.Single(b) }
    }
    flush()
    return out
}

private fun toolName(b: ChatBlockDto) = b.text.removePrefix("tool:").trim()

private fun toolIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        n.startsWith("bash") || n.contains("shell") || n.contains("exec") -> Icons.Filled.Terminal
        n.startsWith("read") || n.contains("view") || n.contains("cat") -> Icons.Filled.Description
        n.contains("edit") || n.contains("patch") -> Icons.Filled.Edit
        n.startsWith("write") || n.contains("create") -> Icons.Filled.NoteAdd
        n.contains("web") || n.contains("fetch") || n.contains("browser") -> Icons.Filled.Language
        n.contains("grep") || n.contains("glob") || n.contains("search") -> Icons.Filled.Search
        else -> Icons.Filled.Build
    }
}

@Composable
private fun ChatBlockRow(b: ChatBlockDto) {
    val t = Tok
    when (b.role) {
        "tool" -> {
            val name = toolName(b)
            StepRow(toolIcon(name), name, t.text2)
        }
        "result" -> ResultLine(b.text.removePrefix("result:"), error = false)
        "error" -> ResultLine(b.text.removePrefix("result:"), error = true)
        "thinking" -> Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
            IconTile(Icons.Filled.Psychology, t.muted, size = 26.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                b.text,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = t.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Mesaj: düz düzyazı — balon yok.
        else -> Text(
            b.text,
            style = MaterialTheme.typography.bodyMedium,
            color = t.text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

// Adım satırı: ikon karosu (tür) + mono başlık. Kenarsız; zemin yok.
@Composable
private fun StepRow(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint, size = 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontFamily = Readout), color = Tok.text, maxLines = 1)
    }
}

// Sonuç: karonun hizasında küçük okuma — başarı sessiz yeşil, hata mercan.
@Composable
private fun ResultLine(text: String, error: Boolean) {
    val t = Tok
    Row(
        Modifier.padding(start = 8.dp + 26.dp + 10.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalPixel(if (error) Signal.Error else Signal.Idle, size = 6.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
            color = if (error) t.danger else t.success,
        )
    }
}

// Grup satırı: "5 araç · Bash, Read, Edit" — dokununca açılır, chevron döner.
// Açık grup hafif zeminde durur, adımlar içinde.
@Composable
private fun ToolGroupRow(blocks: List<ChatBlockDto>) {
    val t = Tok
    var open by remember { mutableStateOf(false) }
    val reduced = rememberReducedMotion()
    val angle by animateFloatAsState(if (open) 90f else 0f, tween(if (reduced) 0 else 130), label = "chev")
    val tools = blocks.filter { it.role == "tool" }
    val errors = blocks.count { it.role == "error" }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (open) t.hover else androidx.compose.ui.graphics.Color.Transparent),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Filled.Build, if (errors > 0) t.danger else t.text2, size = 26.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${tools.size} araç" + if (errors > 0) " · $errors hata" else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = t.text,
                )
                Text(
                    tools.map { toolName(it) }.distinct().joinToString(", "),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
                    color = t.muted,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (open) "Grubu kapat" else "Grubu aç",
                tint = t.muted,
                modifier = Modifier.size(18.dp).rotate(angle),
            )
        }
        if (open) {
            Box(Modifier.padding(bottom = 4.dp)) {
                Column { blocks.forEach { ChatBlockRow(it) } }
            }
        }
    }
}
