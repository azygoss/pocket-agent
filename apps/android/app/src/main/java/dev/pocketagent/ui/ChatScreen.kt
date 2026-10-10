// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pocketagent.net.ChatBlockDto
import dev.pocketagent.ui.theme.Code
import dev.pocketagent.ui.theme.Motion
import dev.pocketagent.ui.theme.PillShape
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.rememberReducedMotion

// P14: agent transcript görünümü. Bloklar gateway /chat üzerinden gelir
// (tam içerik yalnız SSH tünelinde; backend yalnız ≤256 karakter özet görür).
// Sunum (docs/design.md): bir zaman çizelgesi — agent'ın sözleri düz metin,
// her araç çağrısı türünü söyleyen renkli karo + ad, sonucu altında küçük
// bir rozet. Art arda üç+ araç tek satıra katlanır, dokununca açılır.
@Composable
fun ChatDialog(title: String, blocks: List<ChatBlockDto>, onClose: () -> Unit) {
    FullScreenDialog(onDismiss = onClose) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))) {
            PocketTopBar(
                title,
                onBack = onClose,
                backIcon = Icons.Rounded.Close,
                backDesc = "Kapat",
                insetTop = false,
                subtitle = {
                    Text(
                        "${blocks.count { it.role == "tool" }} araç · ${blocks.count { it.role == "message" }} mesaj",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            if (blocks.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.Forum,
                        title = "Gösterilecek adım yok",
                        body = "Blok yok — transcript boş ya da tanınmadı.",
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(foldSteps(blocks)) { item ->
                        Box(Modifier.widthIn(max = 720.dp)) {
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
        n.startsWith("bash") || n.contains("shell") || n.contains("exec") -> Icons.Rounded.Terminal
        n.startsWith("read") || n.contains("view") || n.contains("cat") -> Icons.Rounded.Description
        n.contains("edit") || n.contains("patch") -> Icons.Rounded.Edit
        n.startsWith("write") || n.contains("create") -> Icons.AutoMirrored.Rounded.NoteAdd
        n.contains("web") || n.contains("fetch") || n.contains("browser") -> Icons.Rounded.Language
        n.contains("grep") || n.contains("glob") || n.contains("search") -> Icons.Rounded.Search
        else -> Icons.Rounded.Build
    }
}

@Composable
private fun ChatBlockRow(b: ChatBlockDto) {
    val t = Tok
    when (b.role) {
        "tool" -> StepRow(toolIcon(toolName(b)), toolName(b))
        "result" -> ResultChip(b.text.removePrefix("result:"), error = false)
        "error" -> ResultChip(b.text.removePrefix("result:"), error = true)
        "thinking" -> Row(Modifier.padding(vertical = Space.sm), verticalAlignment = Alignment.Top) {
            IconTile(Icons.Rounded.Psychology, size = 32.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Düşünüyor", style = MaterialTheme.typography.labelMedium, color = t.text2)
                Text(b.text, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = t.text2)
            }
        }
        // Mesaj: düz düzyazı — balon yok.
        else -> Text(
            b.text,
            style = MaterialTheme.typography.bodyLarge,
            color = t.text,
            modifier = Modifier.fillMaxWidth().padding(vertical = Space.md),
        )
    }
}

@Composable
private fun StepRow(icon: ImageVector, label: String) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tone = Tok.busy, size = 32.dp)
        Spacer(Modifier.width(Space.md))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Code), color = Tok.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Sonuç rozeti: karonun hizasında; başarı yeşil tik, hata mercan çarpı.
@Composable
private fun ResultChip(text: String, error: Boolean) {
    val tone = if (error) Tok.danger else Tok.live
    Row(Modifier.padding(start = 32.dp + Space.md, bottom = 4.dp)) {
        Row(
            Modifier.clip(PillShape).background(tone.soft).padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (error) Icons.Rounded.Close else Icons.Rounded.Check, contentDescription = if (error) "hata" else "başarılı", tint = tone.onSoft, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium.copy(fontFamily = Code, fontWeight = FontWeight.Normal), color = tone.onSoft, maxLines = 2)
        }
    }
}

// Grup satırı: "5 araç · 1 hata" + araç adları; dokununca açılır, ok döner.
@Composable
private fun ToolGroupRow(blocks: List<ChatBlockDto>) {
    val t = Tok
    var open by remember { mutableStateOf(false) }
    val reduced = rememberReducedMotion()
    val angle by animateFloatAsState(if (open) 90f else 0f, tween(if (reduced) 0 else Motion.Fast), label = "chev")
    val tools = blocks.filter { it.role == "tool" }
    val errors = blocks.count { it.role == "error" }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (open) t.card else androidx.compose.ui.graphics.Color.Transparent),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .tap({ open = !open })
                .defaultMinSize(minHeight = 52.dp)
                .padding(horizontal = if (open) Space.md else 0.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Rounded.Build, tone = if (errors > 0) t.danger else t.neutral, size = 32.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "${tools.size} araç" + if (errors > 0) " · $errors hata" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (errors > 0) t.danger.text else t.text,
                )
                Text(
                    tools.map { toolName(it) }.distinct().joinToString(", "),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code),
                    color = t.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = if (open) "Grubu kapat" else "Grubu aç",
                tint = t.text2,
                modifier = Modifier.size(22.dp).rotate(angle),
            )
        }
        if (open) {
            Column(Modifier.padding(start = Space.md, end = Space.md, bottom = Space.sm)) {
                blocks.forEach { ChatBlockRow(it) }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
