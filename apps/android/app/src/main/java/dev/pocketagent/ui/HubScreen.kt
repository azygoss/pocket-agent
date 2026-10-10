// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.data.ProfileRepository
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.SavedProfile
import dev.pocketagent.transport.SessionHandle
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.ui.theme.Code
import dev.pocketagent.ui.theme.InnerShape
import dev.pocketagent.ui.theme.LocalMonoFont
import dev.pocketagent.ui.theme.LocalConsoleTheme
import dev.pocketagent.ui.theme.Motion
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

// ── Oturumlar (kök ekran) ───────────────────────────────────────────────────
// Uygulamanın tek ana yüzeyi: şu an çalışanlar üstte (açık oturumlar,
// host'ta yaşayan tmux'lar, agent olayları), başlatıcılar altta (hızlı
// başlat profilleri, hostlar). Oturuma dokununca terminal alttan açılır;
// aşağı kaydırılan sayfada "terminale dön" şeridi belirir.

private val NewHost = SavedConnection(name = "", host = "", port = 22, user = "", credentialRef = "ram:password")
private val NewProfile = SavedProfile(name = "", command = "")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HubScreen(
    sessions: SessionManager,
    repo: ConnectionRepository,
    profiles: ProfileRepository,
    modifier: Modifier = Modifier,
    inbox: InboxViewModel? = null,
    syncStatus: String? = null,
    app: dev.pocketagent.android.App? = null,
    settings: SettingsViewModel? = null,
    pendingAdd: MutableStateFlow<SavedConnection?>? = null,
    showDock: Boolean = true,
    onOpenSession: () -> Unit,
    onOpenFiles: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val sessionList by sessions.sessions.collectAsState()
    val activeId by sessions.activeId.collectAsState()
    val customNames by sessions.customNames.collectAsState()
    val tmuxNames by sessions.tmuxNames.collectAsState()
    val remote by sessions.remote.collectAsState()
    val saved by repo.items.collectAsState()
    val profileList by profiles.items.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val notify = rememberNotify()
    val t = Tok

    var editor by remember { mutableStateOf<SavedConnection?>(null) }
    var deletingHost by remember { mutableStateOf<SavedConnection?>(null) }
    var profileEditor by remember { mutableStateOf<SavedProfile?>(null) }
    var profileMenu by remember { mutableStateOf<SavedProfile?>(null) }
    var deletingProfile by remember { mutableStateOf<SavedProfile?>(null) }
    var profileHostPick by remember { mutableStateOf<SavedProfile?>(null) }
    var sessionMenu by remember { mutableStateOf<SessionHandle?>(null) }
    var renaming by remember { mutableStateOf<SessionHandle?>(null) }
    var showPair by remember { mutableStateOf(false) }
    var pairBusy by remember { mutableStateOf(false) }
    var pairError by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<List<dev.pocketagent.transport.SshConfigEntry>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var allEvents by rememberSaveable { mutableStateOf(false) }

    // Host'taki pa-* oturumlarını keşfet: aktif oturumu olan her conn'in
    // exec kanalından, 15s'de bir. Başka cihazların açtığı terminaller
    // "host'ta çalışan" satırı olarak görünür — dokunup aynı tmux'a attach.
    LaunchedEffect(sessionList.size) {
        while (true) {
            sessions.discoverRemote()
            kotlinx.coroutines.delay(15_000)
        }
    }

    // pocketagent://add?host=… ve terminalden "parola gerekli" → düzenleyici.
    if (pendingAdd != null) {
        val pending by pendingAdd.collectAsState()
        LaunchedEffect(pending) {
            pending?.let { editor = it; pendingAdd.value = null }
        }
    }

    val sshConfigPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { s -> s.readBytes().decodeToString() } ?: ""
            val entries = dev.pocketagent.transport.SshConfig.parse(text)
            if (entries.isEmpty()) notify("Dosyada host bulunamadı") else importPreview = entries
        }.onFailure { notify("Dosya okunamadı: ${it.message}") }
    }

    val startPair: (String, String) -> Unit = { backend, code ->
        pairClaim(backend, code, app, repo, sessions, scope, onConnected = { showPair = false; onOpenSession() },
            onBusy = { pairBusy = it }, onError = { pairError = it; showPair = true })
    }
    val qrLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract(),
    ) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        val parsed = dev.pocketagent.net.PairingClient.parseQr(contents, settings?.backendUrl ?: "")
        if (parsed == null) {
            pairError = "Bu bir Pocket Agent kodu değil — QR \"pa1|\" ile başlamalı."
            showPair = true
        } else {
            startPair(parsed.first, parsed.second)
        }
    }
    val camPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            qrLauncher.launch(
                com.journeyapps.barcodescanner.ScanOptions().apply {
                    setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                    setPrompt("Host'taki QR kodunu okut")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                },
            )
        } else {
            pairError = "Kamera izni verilmedi. 8 haneli kodu elle girebilirsin."
            showPair = true
        }
    }
    val openPair = { pairError = null; showPair = true }
    val openImport = { sshConfigPicker.launch(arrayOf("*/*")) }

    // Bağlan: açık oturum varsa ona geç, secret yoksa düzenleyici sorar.
    fun connect(c: SavedConnection) {
        val open = sessionList.firstOrNull { it.conn.id == c.id }
        val secret = repo.secret(c.id)
        if (secret == null && open == null) editor = c else {
            sessions.open(c, secret)
            onOpenSession()
        }
    }
    fun newSession(c: SavedConnection) {
        val secret = repo.secret(c.id)
        if (secret == null) editor = c else {
            sessions.open(c, secret, forceNew = true)
            onOpenSession()
        }
    }
    // Profil: her zaman yeni oturum — mevcut kabuğun içine komut yazılmaz.
    fun openProfileSession(conn: SavedConnection, p: SavedProfile) {
        val secret = repo.secret(conn.id)
        if (secret == null) editor = conn else {
            sessions.open(conn, secret, forceNew = true, startupCommand = p.command)
            onOpenSession()
        }
    }
    fun launchProfile(p: SavedProfile) {
        val conn = p.connectionId?.let { cid -> saved.firstOrNull { it.id == cid } }
        if (conn == null) profileHostPick = p else openProfileSession(conn, p)
    }
    fun openSession(h: SessionHandle) {
        sessions.setActive(h.id)
        if (h.controller.canReconnect()) h.controller.reconnect()
        onOpenSession()
    }
    // Host'ta yaşayan tmux oturumuna attach; secret yoksa önce düzenleyici.
    fun attachRemote(c: SavedConnection, r: dev.pocketagent.transport.RemoteTerm) {
        val secret = repo.secret(c.id)
        if (secret == null) { editor = c; return }
        sessions.open(
            c, secret,
            forceNew = true,
            tmuxName = r.tmux,
            customName = r.name,
            // Başkasının oturumu: kapatma yalnız ayırsın.
            shared = r.device != sessions.deviceId,
        )
        onOpenSession()
    }
    // Agent olayı → agent'ın çalıştığı oturum: bu cihazda açıksa ona geçer,
    // host'ta yaşıyorsa attach eder; eşleşme yoksa null (yalnız okundu).
    fun eventTarget(e: InboxRow): (() -> Unit)? {
        val tm = tmuxOfEvent(e.sessionId) ?: return null
        sessionList.firstOrNull { tmuxNames[it.id] == tm }?.let { h -> return { openSession(h) } }
        for ((connId, terms) in remote) {
            val c = saved.firstOrNull { it.id == connId } ?: continue
            terms.firstOrNull { it.tmux == tm }?.let { r -> return { attachRemote(c, r) } }
        }
        return null
    }

    // Yerelde açık tmux uzak listede kalırsa (probe yarışı) aynı oturum iki
    // kez görünürdü — render'da da ele.
    val openTmux = tmuxNames.values.toSet()
    val remoteItems = remote.flatMap { (connId, terms) ->
        saved.firstOrNull { it.id == connId }?.let { c -> terms.map { c to it } } ?: emptyList()
    }.filter { it.second.tmux !in openTmux }
    val events = inbox?.rows.orEmpty()
    val unread = events.count { it.unread }
    val pendingApprovals = events.filter { it.unread && isApproval(it.category) }
    val shownEvents = if (allEvents) events.take(24) else events.take(4)
    val hosts = remember(saved, query) {
        val sorted = saved.sortedByDescending { it.lastConnectedAt }
        if (query.isBlank()) sorted
        else sorted.filter { it.name.contains(query, true) || it.host.contains(query, true) || it.user.contains(query, true) }
    }
    val backendTrouble = syncStatus != null && syncStatus != "bağlı" && syncStatus != "kapalı"
    val firstRun = saved.isEmpty() && sessionList.isEmpty()

    val listState = rememberLazyListState()
    val sessionsVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { (it.key as? String)?.startsWith("s-") == true }
        }
    }
    val active = sessionList.firstOrNull { it.id == activeId } ?: sessionList.lastOrNull()
    val dockVisible = showDock && active != null && !sessionsVisible

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = if (showDock && active != null) 112.dp else 40.dp),
        ) {
            item(key = "header") {
                HubHeader(onPair = openPair, onSettings = onOpenSettings)
            }

            if (firstRun) {
                item(key = "setup") {
                    SetupGuide(
                        onCopy = {
                            clipboard.setText(AnnotatedString("pocket-agent onboard"))
                            notify("Komut kopyalandı")
                        },
                        onPair = openPair,
                        onAdd = { editor = NewHost },
                        onImport = openImport,
                    )
                }
                return@LazyColumn
            }

            if (backendTrouble) {
                item(key = "backend") {
                    Banner(
                        Status.Warn,
                        "Backend'e ulaşılamıyor",
                        body = "Agent olayları güncellenmiyor. ${syncStatus.orEmpty()}",
                        modifier = Modifier.padding(top = Space.sm),
                        actions = { GhostButton("Ayarlar", onClick = onOpenSettings) },
                    )
                }
            }

            // ── Bekleyen onay: en acil şey en üstte; agent'ın oturumuna götürür.
            if (pendingApprovals.isNotEmpty()) {
                item(key = "approvals") {
                    val first = pendingApprovals.first()
                    val go = eventTarget(first)
                    val who = first.source.ifBlank { "agent" }.replaceFirstChar { it.titlecase(java.util.Locale.forLanguageTag("tr")) }
                    Banner(
                        Status.Warn,
                        if (pendingApprovals.size == 1) "$who onay bekliyor" else "${pendingApprovals.size} onay bekliyor",
                        body = first.title,
                        icon = Icons.Rounded.PanTool,
                        modifier = Modifier.padding(top = Space.sm),
                        actions = {
                            GhostButton("Okundu", color = t.text2, onClick = { pendingApprovals.forEach { inbox?.markRead(it.eventId) } })
                            if (go != null) {
                                TonalButton("Terminale git", onClick = { inbox?.markRead(first.eventId); go() })
                            }
                        },
                    )
                }
            }

            // ── Açık oturumlar ─────────────────────────────────────────────
            if (sessionList.isNotEmpty() || remoteItems.isNotEmpty()) {
                item(key = "sec-sessions") {
                    SectionHeader(
                        "Oturumlar",
                        count = if (sessionList.size > 1) "${sessionList.size}" else null,
                        modifier = Modifier.padding(top = Space.lg),
                    )
                }
                itemsIndexed(sessionList, key = { _, h -> "s-" + h.id }, contentType = { _, _ -> "session" }) { idx, h ->
                    val same = sessionList.count { it.conn.id == h.conn.id }
                    val nth = sessionList.take(idx).count { it.conn.id == h.conn.id }
                    val name = customNames[h.id] ?: if (same > 1) "${h.conn.name} · ${nth + 1}" else h.conn.name
                    val tmux = tmuxNames[h.id]
                    val agent = tmux?.let { tm -> events.firstOrNull { tmuxOfEvent(it.sessionId) == tm } }
                    SessionCard(
                        h = h,
                        name = name,
                        agent = agent,
                        onOpen = { openSession(h) },
                        onRename = { renaming = h },
                        onMenu = { sessionMenu = h },
                        modifier = Modifier.padding(bottom = Space.md),
                    )
                }
                if (remoteItems.isNotEmpty()) {
                    item(key = "sec-remote") {
                        Text(
                            "Host'ta çalışıyor, bu cihazda açık değil",
                            style = MaterialTheme.typography.labelLarge,
                            color = t.text2,
                            modifier = Modifier.padding(top = Space.xs, bottom = Space.sm, start = 4.dp),
                        )
                    }
                    itemsIndexed(remoteItems, key = { _, p -> "r-" + p.second.tmux }) { i, (c, r) ->
                        val enabled = repo.hasSavedSecret(c.id)
                        GroupRow(i, remoteItems.size) {
                            RemoteSessionRow(
                                name = r.name ?: r.tmux,
                                tmux = r.tmux,
                                address = "${c.user}@${c.host}",
                                otherDevice = r.device != null && r.device != sessions.deviceId,
                                enabled = enabled,
                                onAttach = { attachRemote(c, r) },
                            )
                        }
                    }
                }
            }

            // ── Agent etkinliği ────────────────────────────────────────────
            if (events.isNotEmpty()) {
                item(key = "sec-agents") {
                    SectionHeader(
                        "Agent etkinliği",
                        count = if (unread > 0) "$unread yeni" else null,
                        modifier = Modifier.padding(top = Space.section - Space.md),
                        action = if (events.size > 4) {
                            { GhostButton(if (allEvents) "Daralt" else "Tümü (${events.size})", onClick = { allEvents = !allEvents }) }
                        } else null,
                    )
                }
                itemsIndexed(shownEvents, key = { _, e -> "e-" + e.eventId }) { i, e ->
                    GroupRow(i, shownEvents.size) {
                        val go = eventTarget(e)
                        AgentEventRow(e, canOpen = go != null) {
                            inbox?.markRead(e.eventId)
                            go?.invoke()
                        }
                    }
                }
            }

            // ── Hızlı başlat ───────────────────────────────────────────────
            if (saved.isNotEmpty()) {
                item(key = "sec-quick") {
                    SectionHeader(
                        "Hızlı başlat",
                        modifier = Modifier.padding(top = Space.section - Space.md),
                        action = { GhostButton("Profil ekle", onClick = { profileEditor = NewProfile }, icon = Icons.Rounded.Add) },
                    )
                }
                item(key = "quick") {
                    if (profileList.isEmpty()) {
                        PocketCard(onClick = { profileEditor = NewProfile }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Rounded.PlayArrow, tone = t.accent)
                                Spacer(Modifier.width(Space.md + 2.dp))
                                Text(
                                    "Bir komutu tek dokunuşla yeni oturumda başlat — örneğin claude ya da htop.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = t.text2,
                                )
                            }
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            profileList.forEach { p ->
                                ProfileChip(
                                    p,
                                    hostName = saved.firstOrNull { it.id == p.connectionId }?.name,
                                    onLaunch = { launchProfile(p) },
                                    onMenu = { profileMenu = p },
                                )
                            }
                        }
                    }
                }
            }

            // ── Hostlar ────────────────────────────────────────────────────
            item(key = "sec-hosts") {
                SectionHeader(
                    "Hostlar",
                    count = "${saved.size}",
                    modifier = Modifier.padding(top = Space.section - Space.md),
                    action = { GhostButton("Host ekle", onClick = { editor = NewHost }, icon = Icons.Rounded.Add) },
                )
                if (sessionList.isEmpty()) {
                    Text(
                        "Oturum açmak için bir host'a dokun.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.text2,
                        modifier = Modifier.padding(start = 4.dp, bottom = Space.sm),
                    )
                }
            }
            if (saved.size > 5) {
                item(key = "search") {
                    PocketTextField(
                        query, { query = it },
                        placeholder = "Ad, host ya da kullanıcı ara",
                        leading = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailing = if (query.isNotEmpty()) {
                            { IconAction(Icons.Rounded.Close, "Aramayı temizle", { query = "" }) }
                        } else null,
                        modifier = Modifier.padding(bottom = Space.md),
                    )
                }
            }
            itemsIndexed(hosts, key = { _, c -> "h-" + c.id }, contentType = { _, _ -> "host" }) { i, c ->
                val open = sessionList.firstOrNull { it.conn.id == c.id }
                val st = open?.controller?.state?.collectAsState()?.value
                GroupRow(i, hosts.size) {
                    HostRow(
                        conn = c,
                        state = st,
                        hasSecret = repo.hasSavedSecret(c.id),
                        onConnect = { connect(c) },
                        onNewSession = { newSession(c) },
                        onEdit = { editor = c },
                        onDelete = { deletingHost = c },
                    )
                }
            }
            if (hosts.isEmpty() && query.isNotBlank()) {
                item(key = "no-match") {
                    Text(
                        "\"$query\" ile eşleşen host yok.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.text2,
                        modifier = Modifier.padding(vertical = Space.md, horizontal = 4.dp),
                    )
                }
            }
            item(key = "more") {
                Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    GhostButton("QR ile eşle", onClick = openPair, icon = Icons.Rounded.QrCodeScanner, color = t.text2)
                    GhostButton("~/.ssh/config", onClick = openImport, icon = Icons.Rounded.FileOpen, color = t.text2)
                }
            }
        }

        // ── Terminale dön şeridi ────────────────────────────────────────────
        val reduced = rememberReducedMotion()
        AnimatedVisibility(
            visible = dockVisible,
            enter = if (reduced) fadeIn(tween(0)) else slideInVertically(tween(Motion.Standard, easing = Motion.Enter)) { it } + fadeIn(),
            exit = if (reduced) fadeOut(tween(0)) else slideOutVertically(tween(Motion.Fast, easing = Motion.Exit)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            active?.let { h ->
                SessionDock(
                    h,
                    name = customNames[h.id] ?: h.conn.name,
                    others = sessionList.size - 1,
                    onOpen = { openSession(h) },
                )
            }
        }
    }

    // ── Katmanlar ───────────────────────────────────────────────────────────

    sessionMenu?.let { h ->
        val st = h.controller.state.collectAsState().value
        // "Oturumu kapat…" aynı sayfayı onay görünümüne çevirir — ikinci bir
        // katman açılmaz, geri dönüş "Vazgeç" ile.
        var confirmClose by remember(h) { mutableStateOf(false) }
        val shared = sessions.isShared(h.id)
        val name = customNames[h.id] ?: h.conn.name
        PocketSheet(
            onDismiss = { sessionMenu = null },
            title = if (confirmClose) "$name kapatılsın mı?" else name,
            subtitle = if (confirmClose) closeSubtitle(shared, tmuxNames[h.id])
            else "${h.conn.user}@${h.conn.host}" + (tmuxNames[h.id]?.let { " · $it" } ?: ""),
            subtitleMono = !confirmClose,
        ) { dismiss ->
            if (confirmClose) {
                CloseSessionActions(h, sessions, shared, onDone = { sessionMenu = null }, onCancel = { confirmClose = false })
            } else {
                SheetAction(Icons.Rounded.Terminal, "Terminali aç", onClick = { dismiss(); openSession(h) })
                SheetAction(
                    Icons.Rounded.Folder, "Dosyalar",
                    sublabel = if (st == ConnectionState.ACTIVE) "SFTP ve workspace" else "Önce bağlanmalı",
                    enabled = st == ConnectionState.ACTIVE,
                    onClick = { dismiss(); sessions.setActive(h.id); onOpenFiles() },
                )
                SheetAction(Icons.Rounded.Add, "Aynı host'ta yeni oturum", onClick = { dismiss(); newSession(h.conn) })
                SheetAction(Icons.Rounded.Edit, "Yeniden adlandır", onClick = { dismiss(); renaming = h })
                if (h.controller.canReconnect()) {
                    SheetAction(Icons.Rounded.Refresh, "Yeniden bağlan", onClick = { dismiss(); h.controller.reconnect() })
                }
                InsetDivider(Space.xxl)
                SheetAction(Icons.Rounded.Close, "Oturumu kapat…", danger = true, onClick = { confirmClose = true })
            }
        }
    }
    renaming?.let { h ->
        RenameSessionDialog(
            connName = h.conn.name,
            initial = customNames[h.id],
            onDismiss = { renaming = null },
            onSave = { name -> sessions.rename(h.id, name); renaming = null },
        )
    }

    editor?.let { initial ->
        HostEditor(
            initial = initial,
            onDismiss = { editor = null },
            onSave = { conn, secret, remember ->
                scope.launch {
                    runCatching { repo.upsert(conn, secret, remember) }
                        .onSuccess {
                            editor = null
                            notify(if (conn.id.isBlank()) "${conn.name} eklendi" else "${conn.name} kaydedildi")
                        }
                        .onFailure { notify("Kaydedilemedi: ${it.message ?: "hata"}") }
                }
            },
            onDelete = if (initial.id.isNotBlank()) {
                { editor = null; deletingHost = initial }
            } else null,
            existing = saved,
        )
    }
    deletingHost?.let { c ->
        ConfirmDialog(
            title = "${c.name} silinsin mi?",
            message = "Kayıtlı parola veya anahtar da bu cihazdan silinir. Host'taki oturumlara dokunulmaz.",
            confirmLabel = "Sil",
            onConfirm = {
                scope.launch { repo.delete(c.id) }
                deletingHost = null
                notify("${c.name} silindi")
            },
            onDismiss = { deletingHost = null },
        )
    }

    profileEditor?.let { p ->
        ProfileEditor(
            initial = p,
            conns = saved,
            onDismiss = { profileEditor = null },
            onSave = { np ->
                scope.launch {
                    runCatching { profiles.upsert(np) }
                        .onSuccess { profileEditor = null }
                        .onFailure { notify("Profil kaydedilemedi: ${it.message ?: "hata"}") }
                }
            },
        )
    }
    profileMenu?.let { p ->
        PocketSheet(
            onDismiss = { profileMenu = null },
            title = p.name,
            subtitle = p.command,
            subtitleMono = true,
        ) { dismiss ->
            SheetAction(Icons.Rounded.PlayArrow, "Başlat", onClick = { dismiss(); launchProfile(p) })
            SheetAction(Icons.Rounded.Edit, "Düzenle", onClick = { dismiss(); profileEditor = p })
            InsetDivider(Space.xxl)
            SheetAction(Icons.Rounded.Delete, "Sil", danger = true, onClick = { dismiss(); deletingProfile = p })
        }
    }
    deletingProfile?.let { p ->
        ConfirmDialog(
            title = "${p.name} silinsin mi?",
            message = "Profil kaldırılır; açık oturumlar etkilenmez.",
            confirmLabel = "Sil",
            onConfirm = {
                scope.launch { profiles.delete(p.id) }
                deletingProfile = null
            },
            onDismiss = { deletingProfile = null },
        )
    }
    profileHostPick?.let { p ->
        HostPickerSheet(
            title = p.name,
            subtitle = "Bu profil hangi host'ta açılsın?",
            hosts = saved,
            onDismiss = { profileHostPick = null },
            onPick = { c -> profileHostPick = null; openProfileSession(c, p) },
        )
    }

    if (showPair) {
        PairingSheet(
            backendUrl = settings?.backendUrl ?: "",
            busy = pairBusy,
            error = pairError,
            onDismiss = { showPair = false },
            onScan = { camPermission.launch(android.Manifest.permission.CAMERA) },
            onCode = { backend, code ->
                if (settings != null && backend.isNotBlank() && backend != settings.backendUrl) {
                    settings.setBackend(backend, settings.tenantToken.ifBlank { "default" })
                }
                val parsed = dev.pocketagent.net.PairingClient.parseQr(code, backend)
                if (parsed == null) {
                    pairError = if (backend.isBlank()) "Kodla eşlemek için backend adresi gerekli." else "Kod 8 karakter olmalı (XXXX-XXXX)."
                } else {
                    pairError = null
                    startPair(parsed.first, parsed.second)
                }
            },
        )
    }

    importPreview?.let { entries ->
        ImportSheet(
            entries = entries,
            onDismiss = { importPreview = null },
            onImport = { chosen ->
                scope.launch {
                    chosen.forEach { e ->
                        runCatching { repo.upsert(dev.pocketagent.transport.SshConfig.toConnection(e), null) }
                    }
                    importPreview = null
                    notify("${chosen.size} host içe aktarıldı")
                }
            },
        )
    }
}

// ── Başlık ──────────────────────────────────────────────────────────────────

@Composable
private fun HubHeader(onPair: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = 30.dp)
        Spacer(Modifier.width(Space.md))
        Text("Pocket Agent", style = MaterialTheme.typography.titleLarge, color = Tok.text, modifier = Modifier.weight(1f))
        IconAction(Icons.Rounded.QrCodeScanner, "QR ile eşle", onPair)
        IconAction(Icons.Rounded.Settings, "Ayarlar", onSettings)
    }
}

// İlk açılış: üç adımlık kurulum — komut, eşleme, elle ekleme.
@Composable
private fun SetupGuide(onCopy: () -> Unit, onPair: () -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    val t = Tok
    Column(Modifier.fillMaxWidth().padding(top = Space.xl)) {
        Text("Cebindeki terminal", style = MaterialTheme.typography.headlineMedium, color = t.text)
        Spacer(Modifier.height(Space.sm))
        Text(
            "Kendi sunucuna SSH ile bağlan, tmux oturumlarını kaldığın yerden sürdür, agent'larının ne yaptığını gör.",
            style = MaterialTheme.typography.bodyLarge,
            color = t.text2,
        )
        Spacer(Modifier.height(Space.xxl))
        StepCard(1, "Host'ta çalıştır", "Daemon, gateway ve eşleme kodunu tek komutla kurar.") {
            CodeLine("pocket-agent onboard", onCopy = onCopy)
        }
        Spacer(Modifier.height(Space.md))
        StepCard(2, "Telefonu eşle", "Ekrandaki QR'ı tara ya da 8 haneli kodu gir.") {
            PrimaryButton("QR veya kodla eşle", onClick = onPair, icon = Icons.Rounded.QrCodeScanner, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Space.md))
        StepCard(null, "Ya da elle ekle", "Host, kullanıcı ve parola ya da SSH anahtarıyla.") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlineButton("Host ekle", onClick = onAdd, icon = Icons.Rounded.Add)
                GhostButton("~/.ssh/config", onClick = onImport, color = t.text2)
            }
        }
        Spacer(Modifier.height(Space.lg))
        Text(
            "Terminal ve dosya trafiği yalnız senin SSH bağlantından akar. Parolalar ve anahtarlar Keystore ile şifrelenir.",
            style = MaterialTheme.typography.bodySmall,
            color = t.text2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun StepCard(n: Int?, title: String, body: String, content: @Composable () -> Unit) {
    val t = Tok
    PocketCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(if (n != null) t.accent.soft else t.raised),
                contentAlignment = Alignment.Center,
            ) {
                if (n != null) {
                    Text("$n", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = t.accent.onSoft)
                } else {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = t.text2, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = t.text)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = t.text2)
            }
        }
        Spacer(Modifier.height(Space.md))
        content()
    }
}

// ── Gruplu satır ────────────────────────────────────────────────────────────
// Bir listenin satırları ayrı öğelerdir (sanallaştırılır) ama tek kart gibi
// görünür: ilk satır üst, son satır alt köşeleri yuvarlar; araya ayırıcı.

@Composable
fun GroupRow(index: Int, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val r = 22.dp
    val shape: Shape = RoundedCornerShape(
        topStart = if (index == 0) r else 4.dp,
        topEnd = if (index == 0) r else 4.dp,
        bottomStart = if (index == count - 1) r else 4.dp,
        bottomEnd = if (index == count - 1) r else 4.dp,
    )
    Column(modifier.fillMaxWidth().padding(bottom = 2.dp).clip(shape).background(Tok.card)) { content() }
}

// ── Oturum kartı ────────────────────────────────────────────────────────────
// Canlı mini terminal: buffer'ın son dolu satırları, en fazla 4×/sn
// örneklenir. Dokun → terminal; uzun bas → yeniden adlandır; ⋮ → seçenekler.

@Composable
private fun SessionCard(
    h: SessionHandle,
    name: String,
    agent: InboxRow?,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tok
    val console = LocalConsoleTheme.current
    val st by h.controller.state.collectAsState()
    val retry by h.controller.retryAttempt.collectAsState()
    val lines by rememberPreviewLines(h, 5)
    PocketCard(
        modifier = modifier,
        onClick = onOpen,
        onLongClick = onRename,
        onLongClickLabel = "Yeniden adlandır",
        contentPadding = PaddingValues(start = Space.lg, end = 4.dp, top = Space.md, bottom = Space.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HostAvatar(h.conn.name, h.conn.host + h.conn.user, size = 36.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${h.conn.user}@${h.conn.host}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code),
                    color = t.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Space.sm))
            StatusChip(if (retry > 0) Status.Warn else st.status(), st.label(retry))
            IconAction(Icons.Rounded.MoreVert, "$name seçenekleri", onMenu)
        }
        Spacer(Modifier.height(Space.md))
        Column(
            Modifier
                .padding(end = Space.md)
                .fillMaxWidth()
                .clip(InnerShape)
                .background(Color(console.term.background))
                .border(1.dp, t.line, InnerShape)
                // Ham terminal satırları ekran okuyucuya okunmaz; kart adı ve
                // durumu yeterli.
                .clearAndSetSemantics { }
                .padding(horizontal = Space.md, vertical = 10.dp)
                .heightIn(min = 72.dp),
        ) {
            if (lines.isEmpty()) {
                Text(
                    when (st) {
                        ConnectionState.ACTIVE -> "Hazır. Dokun ve yazmaya başla."
                        ConnectionState.CONNECTING -> "Bağlanıyor…"
                        else -> "Çıktı yok"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalMonoFont.current),
                    color = Color(console.term.foreground).copy(alpha = 0.6f),
                )
            }
            lines.forEach { l ->
                Text(
                    l.toAnnotatedString(ansi = console.term.ansi),
                    color = Color(console.term.foreground),
                    fontFamily = LocalMonoFont.current,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (agent != null) {
            val kind = agentKind(agent.category)
            Spacer(Modifier.height(Space.md))
            Row(Modifier.padding(end = Space.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(kind.icon, contentDescription = null, tint = kind.status.tone().fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Space.sm))
                Text(
                    listOf(agent.source.ifBlank { "agent" }, kind.label).joinToString(" · ") + " — " + agent.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Önizleme satırları: ilk değer hemen, sonrası 250ms örneklenmiş; sondaki
// boş ekran satırları atılır (TUI'de alt kısım boş kalmasın).
@Composable
private fun rememberPreviewLines(h: SessionHandle, n: Int) = remember(h) {
    val src = h.controller.vm.lines
    kotlinx.coroutines.flow.flow {
        emit(src.first())
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        emitAll(src.sample(250))
    }.map { ls -> ls.dropLastWhile { it.text.isBlank() }.takeLast(n) }
}.collectAsState(initial = emptyList())

// Host'ta yaşayan ama bu cihazda açık olmayan oturum: dokun → aynı tmux'a attach.
@Composable
private fun RemoteSessionRow(
    name: String,
    tmux: String,
    address: String,
    otherDevice: Boolean,
    enabled: Boolean,
    onAttach: () -> Unit,
) {
    // Parola kayıtlı değilse dokunuş düzenleyiciyi açar (attachRemote).
    ListRow(
        title = name,
        subtitle = "$tmux · $address",
        subtitleMono = true,
        onClick = onAttach,
        leading = {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                StatusDot(Status.Idle, size = 10.dp)
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (otherDevice) Tag("başka cihaz")
                Spacer(Modifier.width(Space.sm))
                if (enabled) {
                    TonalButton("Bağlan", onClick = onAttach)
                } else {
                    GhostButton("Parola gir", onClick = onAttach, color = Tok.text2)
                }
            }
        },
    )
}

// "Terminale dön" şeridi: oturum kartları görünmezken altta durur; aktif
// oturumun son satırını canlı gösterir.
@Composable
private fun SessionDock(h: SessionHandle, name: String, others: Int, onOpen: () -> Unit) {
    val t = Tok
    val st by h.controller.state.collectAsState()
    val retry by h.controller.retryAttempt.collectAsState()
    val lines by rememberPreviewLines(h, 1)
    Row(
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Space.lg, vertical = Space.md)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(t.raised)
            .border(1.dp, t.line, RoundedCornerShape(22.dp))
            .semantics { contentDescription = "Terminale dön: $name" }
            .tap(onOpen)
            .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(if (retry > 0) Status.Warn else st.status(), size = 10.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (others > 0) {
                    Spacer(Modifier.width(Space.sm))
                    Tag("+$others")
                }
            }
            Text(
                lines.lastOrNull()?.text?.trim()?.ifBlank { null } ?: st.label(retry),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Code),
                color = t.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = t.text2, modifier = Modifier.padding(horizontal = Space.sm).size(24.dp))
    }
}

// ── Agent olayı ─────────────────────────────────────────────────────────────

internal data class AgentKind(val status: Status, val label: String, val icon: ImageVector)

internal fun isApproval(category: String) = category == "APPROVAL_REQUIRED" || category == "APPROVAL"

// Olayın tmux oturumu: host hook'u agent bir tmux pane'indeyse oturumu
// "tmux:<ad>" olarak ham yollar (uygulama oraya götürebilsin diye).
internal fun tmuxOfEvent(sessionId: String): String? =
    sessionId.takeIf { it.startsWith("tmux:") }?.removePrefix("tmux:")?.takeIf { it.isNotBlank() }

internal fun agentKind(category: String): AgentKind = when (category) {
    "APPROVAL_REQUIRED", "APPROVAL" -> AgentKind(Status.Warn, "Onay bekliyor", Icons.Rounded.PanTool)
    "ERROR" -> AgentKind(Status.Error, "Hata", Icons.Rounded.ErrorOutline)
    "SESSION_STARTED" -> AgentKind(Status.Busy, "Başladı", Icons.Rounded.PlayArrow)
    "TOOL_RUNNING" -> AgentKind(Status.Busy, "Çalışıyor", Icons.Rounded.Autorenew)
    "TOOL_FINISHED" -> AgentKind(Status.Live, "Adım bitti", Icons.Rounded.Check)
    "TASK_COMPLETE" -> AgentKind(Status.Live, "Tamamlandı", Icons.Rounded.TaskAlt)
    "SESSION_ENDED" -> AgentKind(Status.Idle, "Kapandı", Icons.Rounded.Stop)
    else -> AgentKind(Status.Idle, category.lowercase(), Icons.Rounded.Notifications)
}

@Composable
private fun AgentEventRow(e: InboxRow, canOpen: Boolean, onClick: () -> Unit) {
    val t = Tok
    val kind = agentKind(e.category)
    val tone = kind.status.tone()
    val ts = runCatching { java.time.Instant.parse(e.createdAt).toEpochMilli() }.getOrDefault(0L)
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick, label = if (canOpen) "Terminale git" else "Okundu işaretle")
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(kind.icon, tone = tone, size = 36.dp)
        Spacer(Modifier.width(Space.md + 2.dp))
        Column(Modifier.weight(1f)) {
            // Tek metin: dar ekranda ya da büyük yazıda kırılarak sarar,
            // zaman bilgisi kesilmez.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = tone.text, fontWeight = FontWeight.Medium)) { append(kind.label) }
                    append(" · " + listOf(e.source.ifBlank { "agent" }, relativeTime(ts)).joinToString(" · "))
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                color = t.text2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                e.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (e.unread) FontWeight.Medium else FontWeight.Normal),
                color = if (e.unread) t.text else t.text2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (e.unread) {
            Spacer(Modifier.width(Space.sm))
            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(t.primary).semantics { contentDescription = "okunmadı" })
        }
        // Agent'ın oturumu bulunduysa satır oraya götürür.
        if (canOpen) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = t.text3,
                modifier = Modifier.padding(start = Space.xs, top = 6.dp).size(20.dp),
            )
        }
    }
}

// ── Hızlı başlat ────────────────────────────────────────────────────────────

@Composable
private fun ProfileChip(p: SavedProfile, hostName: String?, onLaunch: () -> Unit, onMenu: () -> Unit) {
    val t = Tok
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(t.card)
            .tap(onLaunch, onMenu, label = "Başlat")
            .heightIn(min = 52.dp)
            .padding(start = Space.md, end = Space.lg, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(t.accent.soft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = t.accent.onSoft, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Space.md - 2.dp))
        Column {
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = t.text, maxLines = 1)
            Text(
                hostName ?: "host seç",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                color = t.text2,
                maxLines = 1,
            )
        }
    }
}

// ── Host satırı ─────────────────────────────────────────────────────────────

@Composable
private fun HostRow(
    conn: SavedConnection,
    state: ConnectionState?,
    hasSecret: Boolean,
    onConnect: () -> Unit,
    onNewSession: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val t = Tok
    ListRow(
        title = conn.name,
        subtitle = "${conn.user}@${conn.host}:${conn.port}",
        subtitleMono = true,
        onClick = onConnect,
        contentPadding = PaddingValues(start = Space.lg, end = 4.dp, top = Space.md, bottom = Space.md),
        leading = { HostAvatar(conn.name, conn.host + conn.user) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state != null) {
                    StatusDot(state.status(), Modifier.semantics { contentDescription = state.label() })
                } else {
                    Text(
                        if (hasSecret) relativeTime(conn.lastConnectedAt) else "parola sorulur",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                        color = t.text2,
                    )
                }
                Box {
                    IconAction(Icons.Rounded.MoreVert, "${conn.name} seçenekleri", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = t.raised) {
                        DropdownMenuItem(
                            text = { Text("Bağlan") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                            onClick = { menu = false; onConnect() },
                        )
                        DropdownMenuItem(
                            text = { Text("Yeni oturum aç") },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            onClick = { menu = false; onNewSession() },
                        )
                        DropdownMenuItem(
                            text = { Text("Düzenle") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Sil", color = t.danger.text) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = t.danger.fg) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}

// ── Marka ───────────────────────────────────────────────────────────────────

// İşaret: nane karo üstünde istem oku ve imleç. Launcher ikonuyla aynı çizim.
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val t = Tok
    // Hep bir başlığın yanında durur → ekran okuyucuya tekrar okunmasın.
    androidx.compose.foundation.Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val s = this.size.width
        drawRoundRect(t.primary, cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.3f))
        val ink = t.onPrimary
        val w = s * 0.1f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.29f, s * 0.31f)
            lineTo(s * 0.49f, s * 0.5f)
            lineTo(s * 0.29f, s * 0.69f)
        }
        drawPath(
            path, ink,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        drawRoundRect(
            ink,
            topLeft = androidx.compose.ui.geometry.Offset(s * 0.55f, s * 0.62f),
            size = androidx.compose.ui.geometry.Size(s * 0.2f, w),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2),
        )
    }
}
