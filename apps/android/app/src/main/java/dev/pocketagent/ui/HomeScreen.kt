// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.data.ConnectionRepository
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SessionHandle
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.ui.theme.LocalConsoleTheme
import dev.pocketagent.ui.theme.LocalMonoFont
import dev.pocketagent.ui.theme.Readout
import dev.pocketagent.ui.theme.SheetShape
import dev.pocketagent.ui.theme.Space

@Composable
fun HomeScreen(
    sessions: SessionManager,
    connections: ConnectionRepository,
    inbox: InboxViewModel? = null,
    syncStatus: String? = null,
    onGoTo: (AppTab) -> Unit,
) {
    val sessionList by sessions.sessions.collectAsState()
    val activeId by sessions.activeId.collectAsState()
    val customNames by sessions.customNames.collectAsState()
    val tmuxNames by sessions.tmuxNames.collectAsState()
    val remote by sessions.remote.collectAsState()
    val saved by connections.items.collectAsState()
    var renaming by remember { mutableStateOf<SessionHandle?>(null) }
    val active = sessionList.firstOrNull { it.id == activeId }
    val state = active?.controller?.state?.collectAsState()?.value ?: ConnectionState.CLOSED
    val t = Tok

    // Host'taki pa-* oturumlarını keşfet: aktif oturumu olan her conn'in
    // exec kanalından, 15s'de bir. Başka cihazların açtığı terminaller
    // "Host'ta açık" bölümüne düşer — dokunup aynı tmux'a attach edilir.
    LaunchedEffect(sessionList.size) {
        while (true) {
            sessions.discoverRemote()
            kotlinx.coroutines.delay(15_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
        ) {
            Spacer(Modifier.height(Space.xl))
            ScreenHeader(greeting(), meta = todayLabel())
            Spacer(Modifier.height(Space.lg))
            // Özet: üç okuma kutusu — canlı oturum, kayıtlı host, backend.
            // İlk kurulumda (host yok) sıfırlar bilgi taşımaz ve onboarding'i
            // ekranın altına iter → gösterilmez.
            val liveCount = sessionList.count { it.controller.state.collectAsState().value == ConnectionState.ACTIVE }
            if (saved.isNotEmpty() || sessionList.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                StatTile(
                    value = "${sessionList.size}",
                    label = "oturum",
                    signal = if (liveCount > 0) Signal.Live else null,
                    modifier = Modifier.weight(1f),
                    onClick = { onGoTo(AppTab.Terminal) },
                )
                StatTile("${saved.size}", "host", modifier = Modifier.weight(1f), onClick = { onGoTo(AppTab.Connections) })
                StatTile(
                    value = when (syncStatus) {
                        null -> "—"
                        "bağlı" -> "bağlı"
                        else -> syncStatus
                    },
                    label = "backend",
                    signal = when (syncStatus) {
                        null -> null
                        "bağlı" -> Signal.Live
                        else -> Signal.NeedsYou
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { onGoTo(AppTab.Settings) },
                )
            }
            Spacer(Modifier.height(Space.xl))

            // ── Oturumlar: canlı terminal önizleme sheet'leri ──────────────
            // Yerel açık oturumlar + host'ta yaşayan diğer pa-* oturumları
            // (bu cihazda açık olmayanlar; diğer cihazlar dahil) aynı satırda —
            // uzak kart "host" çipli, dokun → aynı tmux'a attach.
            val openTmux = tmuxNames.values.toSet()
            val remoteItems = remote.flatMap { (connId, terms) ->
                saved.firstOrNull { it.id == connId }
                    ?.let { c -> terms.map { c to it } } ?: emptyList()
                // Yerelde açık tmux uzak listede kalırsa (probe yarışı)
                // aynı oturum iki kart gösterir — render'da da ele.
            }.filter { it.second.tmux !in openTmux }
            if (sessionList.isNotEmpty() || remoteItems.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Oturumlar")
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onGoTo(AppTab.Terminal) }) {
                        Icon(
                            Icons.Filled.GridView,
                            contentDescription = "Terminale git",
                            tint = t.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    items(sessionList, key = { it.id }) { h ->
                        SessionCard(
                            h,
                            name = customNames[h.id],
                            onRename = { renaming = h },
                        ) {
                            sessions.setActive(h.id)
                            onGoTo(AppTab.Terminal)
                        }
                    }
                    items(remoteItems, key = { "r-" + it.second.tmux }) { (c, r) ->
                        RemoteSessionCard(
                            name = r.name ?: r.tmux,
                            tmux = r.tmux,
                            address = "${c.user}@${c.host}",
                            enabled = connections.hasSavedSecret(c.id),
                            onClick = {
                                sessions.open(
                                    c,
                                    connections.secret(c.id),
                                    forceNew = true,
                                    tmuxName = r.tmux,
                                    customName = r.name,
                                    // Başkasının oturumu: kapatma detach etsin.
                                    shared = r.device != sessions.deviceId,
                                )
                                onGoTo(AppTab.Terminal)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(Space.xl))
            }

            // Kopan oturum için hızlı yol — amber: sana ihtiyaç var.
            if (active != null && state != ConnectionState.ACTIVE && active.controller.canReconnect()) {
                TonalTile(onClick = { active.controller.reconnect(); onGoTo(AppTab.Terminal) }) {
                    SignalPixel(Signal.NeedsYou, size = 8.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text("Yeniden bağlan", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = t.text)
                        Text(
                            "etkin oturum bağlı değil",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.muted,
                            maxLines = 1,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = t.muted, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(Space.xl))
            }

            // ── Agent etkinliği: backend inbox'ı (24s TTL özetler) ─────────
            val events = inbox?.rows.orEmpty().take(5)
            if (events.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Agent etkinliği")
                    Spacer(Modifier.weight(1f))
                    val unread = inbox?.rows.orEmpty().count { it.unread }
                    if (unread > 0) TagPill("$unread yeni", active = true, tone = t.accent)
                }
                Spacer(Modifier.height(Space.sm))
                ConsoleCard(padding = 0.dp) {
                    events.forEachIndexed { i, e ->
                        if (i > 0) SoftDivider(Modifier.padding(start = Space.lg + 18.dp))
                        AgentEventRow(e) { inbox?.markRead(e.eventId) }
                    }
                }
                Spacer(Modifier.height(Space.xl))
            }

            // ── Bağlantılar ────────────────────────────────────────────────
            if (saved.isEmpty()) {
                SectionLabel("Başlangıç")
                Spacer(Modifier.height(Space.sm))
                ConsoleCard {
                    StepRow(1, "Host'ta çalıştır: pocket-agent pair")
                    StepRow(2, "QR'ı tara ya da XXXX-XXXX kodunu gir")
                    StepRow(3, "İlk bağlantıda parmak izini pinle (TOFU)")
                    StepRow(4, "tmux re-attach hazır — kopmaya dayanıklı")
                    Spacer(Modifier.height(Space.md))
                    ConsoleButton(onClick = { onGoTo(AppTab.Connections) }) {
                        Text("İlk hostu ekle")
                    }
                }
            } else {
                SectionLabel("Son bağlantılar")
                Spacer(Modifier.height(Space.sm))
                ConsoleCard(padding = 0.dp) {
                    saved.sortedByDescending { it.lastConnectedAt }.take(4).forEachIndexed { i, c ->
                        if (i > 0) SoftDivider(Modifier.padding(start = Space.lg + 36.dp + Space.md))
                        val open = sessionList.firstOrNull { it.conn.id == c.id }
                        ConnectionTile(
                            seed = c.host + c.user,
                            name = c.name,
                            address = "${c.user}@${c.host}:${c.port}",
                            when_ = relativeTime(c.lastConnectedAt),
                            state = open?.controller?.state?.collectAsState()?.value,
                            onClick = {
                                if (open != null || connections.hasSavedSecret(c.id)) {
                                    sessions.open(c, connections.secret(c.id))
                                    onGoTo(AppTab.Terminal)
                                } else {
                                    onGoTo(AppTab.Connections)
                                }
                            },
                        )
                    }
                }
            }

            // FAB payı.
            Spacer(Modifier.height(104.dp))
        }

        // Yeni host: mürekkep dolgulu kare-yumuşak FAB (mavi sinyale saklı).
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(Space.lg)
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(t.text)
                .semantics { contentDescription = "Yeni host" }
                .clickable { onGoTo(AppTab.Connections) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = t.bg, modifier = Modifier.size(24.dp))
        }

        renaming?.let { h ->
            RenameSessionDialog(
                connName = h.conn.name,
                initial = customNames[h.id],
                onDismiss = { renaming = null },
                onSave = { name -> sessions.rename(h.id, name); renaming = null },
            )
        }
    }
}

// Oturum kartı: gerçek buffer'ın son satırlarıyla mini terminal sheet'i.
// Uzun basma → yeniden adlandırma diyaloğu (ad SessionManager.customNames'te).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    h: SessionHandle,
    name: String?,
    onRename: () -> Unit,
    onClick: () -> Unit,
) {
    val console = LocalConsoleTheme.current
    val t = Tok
    // Önizleme canlı ama kısıtlı: çıktı patlamasında kart en fazla ~4 kez/sn
    // yeniden çizilir ve yalnız son 7 satır taşınır (tam buffer değil).
    // İlk değer hemen (StateFlow.first anında döner), sonrası örneklenmiş.
    val lines by remember(h) {
        val src = h.controller.vm.lines
        kotlinx.coroutines.flow.flow {
            emit(src.first())
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            emitAll(src.sample(250))
        }.map { it.takeLast(7) }
    }.collectAsState(initial = emptyList())
    val st by h.controller.state.collectAsState()
    Column(Modifier.width(220.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(SheetShape)
                .background(Color(console.term.background))
                .border(1.dp, t.border, SheetShape)
                .combinedClickable(onClick = onClick, onLongClick = onRename)
                .padding(start = 12.dp, end = 10.dp, top = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPixel(st, size = 7.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    name ?: h.conn.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = Readout),
                    color = t.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                TagPill(h.controller.vm.badge)
            }
            Spacer(Modifier.height(8.dp))
            if (lines.none { it.text.isNotBlank() }) {
                // Henüz çıktı yok: kart boş görünmesin — durum okuması.
                Text(
                    if (st == ConnectionState.ACTIVE) "› hazır" else "› bağlanıyor…",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalMonoFont.current, fontWeight = FontWeight.Normal),
                    color = t.muted,
                )
            }
            Column {
                lines.forEach { l ->
                    Text(
                        l.toAnnotatedString(ansi = console.term.ansi),
                        color = Color(console.term.foreground),
                        fontFamily = LocalMonoFont.current,
                        fontSize = 7.5.sp,
                        lineHeight = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${h.conn.user}@${h.conn.host}",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
            color = t.muted,
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

// Bağlantı satırı: host sigil'i + ad + mono adres; sağda zaman okuması ya
// da canlı oturumun durum pikseli.
@Composable
private fun ConnectionTile(
    seed: String,
    name: String,
    address: String,
    when_: String,
    state: ConnectionState?,
    onClick: () -> Unit,
) {
    val t = Tok
    ListRow(
        title = name,
        subtitle = address,
        subtitleMono = true,
        onClick = onClick,
        leading = { HostSigil(seed) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state != null) {
                    StatusPixel(state, size = 7.dp)
                } else {
                    Text(
                        when_,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
                        color = t.muted,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = t.muted, modifier = Modifier.size(18.dp))
            }
        },
    )
}

// Host'ta yaşayan ama bu cihazda açık olmayan oturum kartı — SessionCard
// geometrisi, "host" çipi + attach ipucu (scrollback yerine).
// Dokun → aynı tmux'a attach; başka cihazın oturumu paylaşımlı açılır.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteSessionCard(
    name: String,
    tmux: String,
    address: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val console = LocalConsoleTheme.current
    val t = Tok
    val alpha = if (enabled) 1f else 0.5f
    Column(Modifier.width(220.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(SheetShape)
                .background(Color(console.term.background))
                .border(1.dp, t.border, SheetShape)
                .combinedClickable(enabled = enabled, onClick = onClick)
                .padding(start = 12.dp, end = 10.dp, top = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Uzakta canlı, burada bağlı değil — boşta pikseli.
                SignalPixel(Signal.Idle, size = 7.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = Readout),
                    color = t.text.copy(alpha = alpha),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                TagPill("host")
            }
            Spacer(Modifier.height(8.dp))
            listOf(
                "\$ tmux attach -t $tmux",
                "→ host'ta canlı oturum",
                if (enabled) "  dokun: attach" else "  secret gerekli",
            ).forEach { l ->
                Text(
                    l,
                    color = Color(console.term.foreground).copy(alpha = 0.6f * alpha),
                    fontFamily = LocalMonoFont.current,
                    fontSize = 7.5.sp,
                    lineHeight = 10.5.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            address,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
            color = t.muted.copy(alpha = alpha),
            maxLines = 1,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

// Numaralı adım: mono sıra numarası (loş) + metin.
@Composable
private fun StepRow(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            "%02d".format(n),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Readout),
            color = Tok.muted,
            modifier = Modifier.width(28.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Tok.text)
    }
}

// Günün saatine göre selam — başlık bir etiket değil, bir karşılama.
private fun greeting(hour: Int = java.time.LocalTime.now().hour): String = when (hour) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    in 18..22 -> "İyi akşamlar"
    else -> "İyi geceler"
}

private fun todayLabel(): String =
    java.time.LocalDate.now().format(
        java.time.format.DateTimeFormatter.ofPattern("d MMMM EEEE", java.util.Locale.forLanguageTag("tr")),
    ).lowercase(java.util.Locale.forLanguageTag("tr"))

// Okuma kutusu: büyük mono sayı + loş etiket; isteğe bağlı sinyal pikseli.
@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    signal: Signal? = null,
    onClick: () -> Unit,
) {
    val t = Tok
    Column(
        modifier
            .clip(SheetShape)
            .background(t.surface)
            .border(1.dp, t.border, SheetShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = t.muted, modifier = Modifier.weight(1f))
            if (signal != null) SignalPixel(signal, size = 7.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
            color = t.text,
            maxLines = 1,
        )
    }
}

// Agent olayı: kategori sinyal pikseline dönüşür — onay bekliyor amber,
// hata mercan, çalışıyor içi boş mavi, okunmamış tamamlanma dolu mavi.
@Composable
private fun AgentEventRow(e: InboxRow, onClick: () -> Unit) {
    val t = Tok
    val signal = when (e.category) {
        "APPROVAL_REQUIRED", "APPROVAL" -> Signal.NeedsYou
        "ERROR" -> Signal.Error
        "SESSION_STARTED", "TOOL_RUNNING" -> Signal.Running
        else -> if (e.unread) Signal.Live else Signal.Idle
    }
    val ts = runCatching { java.time.Instant.parse(e.createdAt).toEpochMilli() }.getOrDefault(0L)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SignalPixel(signal, Modifier.padding(top = 6.dp), size = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (e.unread) FontWeight.Medium else FontWeight.Normal),
                color = if (e.unread) t.text else t.text2,
                maxLines = 2,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOf(e.source.ifBlank { "agent" }, relativeTime(ts)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Readout, fontWeight = FontWeight.Normal),
                color = t.muted,
            )
        }
    }
}
