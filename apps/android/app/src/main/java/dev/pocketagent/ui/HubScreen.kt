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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.draw.drawWithCache
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
import dev.pocketagent.ui.theme.PillShape
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
    openEvent: MutableStateFlow<String?>? = null,
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
    // Tek "Ekle" girişi: host, eşleme, içe aktarma, hızlı başlat profili.
    var addMenu by remember { mutableStateOf(false) }

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

    // Eşleşme başarılı olunca backend ayarı boşsa host'unkiyle doldurulur;
    // aynı backend'de tenant elle değiştirilmemişse host'un tenant'ı alınır.
    // Başka bir backend ayarlıysa dokunulmaz (kullanıcının bilinçli seçimi).
    fun adoptBackend(url: String, tenant: String?) {
        val s = settings ?: return
        val cur = s.backendUrl.trimEnd('/')
        when {
            cur.isEmpty() -> s.setBackend(url, tenant ?: s.tenantToken.ifBlank { "default" })
            cur == url.trimEnd('/') && tenant != null && s.tenantToken in setOf("", "default") -> s.setBackend(url, tenant)
        }
    }
    val startPair: (String, String) -> Unit = { backend, code ->
        pairClaim(backend, code, app, repo, sessions, scope, onConnected = { showPair = false; onOpenSession() },
            onBusy = { pairBusy = it }, onError = { pairError = it; showPair = true },
            onPaired = ::adoptBackend)
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

    // Onay bildirimleri kapalıysa (izin reddedildi ya da sistemden kapatıldı)
    // Oturumlar söyler. İlk dokunuş izni sorar; reddedildiyse ayarları açar.
    // Uygulamaya dönünce yeniden bakılır.
    fun notificationsOn() = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    var notifOn by remember { mutableStateOf(notificationsOn()) }
    var notifAsked by rememberSaveable { mutableStateOf(false) }
    var notifHidden by rememberSaveable { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) notifOn = notificationsOn()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { notifAsked = true; notifOn = notificationsOn() }
    fun enableNotifications() {
        val canAsk = android.os.Build.VERSION.SDK_INT >= 33 && !notifAsked &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (canAsk) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
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
    val pendingApprovals = events.filter { it.unread && isApproval(it.category) }

    // Onay bildiriminden gelindi: olay ve agent'ın oturumu (açılışta geri
    // yüklenen ya da host'ta keşfedilen) görününce terminaline geçilir.
    // 10 sn içinde bulunamazsa Oturumlar'daki onay kartı yeterli.
    val wanted = openEvent?.collectAsState()?.value
    LaunchedEffect(wanted, events, sessionList, remote) {
        val id = wanted ?: return@LaunchedEffect
        val e = events.firstOrNull { it.eventId == id } ?: return@LaunchedEffect
        val go = eventTarget(e) ?: return@LaunchedEffect
        openEvent.value = null
        inbox?.markRead(id)
        app?.clearApprovalNotification()
        go()
    }
    LaunchedEffect(wanted) {
        if (wanted == null) return@LaunchedEffect
        kotlinx.coroutines.delay(10_000)
        if (openEvent?.value == wanted) openEvent.value = null
    }
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
                HubHeader(onAdd = { addMenu = true }, onSettings = onOpenSettings)
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

            // ── Dikkat: aynı anda en fazla bir kart. Önce bekleyen onay, sonra
            // backend sorunu, sonra kapalı bildirimler.
            when {
                pendingApprovals.isNotEmpty() -> item(key = "attention") {
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
                            GhostButton("Okundu", color = t.text2, onClick = {
                                pendingApprovals.forEach { inbox?.markRead(it.eventId) }
                                app?.clearApprovalNotification()
                            })
                            if (go != null) {
                                TonalButton("Terminale git", onClick = {
                                    inbox?.markRead(first.eventId)
                                    app?.clearApprovalNotification()
                                    go()
                                })
                            }
                        },
                    )
                }
                backendTrouble -> item(key = "attention") {
                    Banner(
                        Status.Warn,
                        "Backend'e ulaşılamıyor",
                        body = "Agent olayları güncellenmiyor. " + syncStatus.orEmpty().removePrefix("hata: "),
                        modifier = Modifier.padding(top = Space.sm),
                        actions = { GhostButton("Ayarlar", onClick = onOpenSettings) },
                    )
                }
                settings?.backendConfigured == true && !notifOn && !notifHidden -> item(key = "attention") {
                    Banner(
                        Status.Idle,
                        "Onay bildirimleri kapalı",
                        body = "Agent onay beklerken telefon haber veremez.",
                        icon = Icons.Rounded.NotificationsOff,
                        modifier = Modifier.padding(top = Space.sm),
                        actions = {
                            GhostButton("Şimdi değil", color = t.text2, onClick = { notifHidden = true })
                            TonalButton(if (notifAsked) "Ayarları aç" else "Bildirimleri aç", onClick = ::enableNotifications)
                        },
                    )
                }
            }

            // ── Oturumlar: bu cihazda açık olanlar, ardından host'ta yaşayıp
            // burada açık olmayanlar; tek grup, sade satırlar.
            val sessionRows = sessionList.size + remoteItems.size
            if (sessionRows > 0) {
                item(key = "sec-sessions") { SectionHeader("Oturumlar", modifier = Modifier.padding(top = Space.lg)) }
                itemsIndexed(sessionList, key = { _, h -> "s-" + h.id }, contentType = { _, _ -> "session" }) { idx, h ->
                    val same = sessionList.count { it.conn.id == h.conn.id }
                    val nth = sessionList.take(idx).count { it.conn.id == h.conn.id }
                    val name = customNames[h.id] ?: if (same > 1) "${h.conn.name} · ${nth + 1}" else h.conn.name
                    val tmux = tmuxNames[h.id]
                    val agent = tmux?.let { tm -> events.firstOrNull { tmuxOfEvent(it.sessionId) == tm } }
                    GroupRow(idx, sessionRows) {
                        SessionRow(
                            h = h,
                            name = name,
                            agent = agent,
                            onOpen = { openSession(h) },
                            onRename = { renaming = h },
                            onMenu = { sessionMenu = h },
                        )
                    }
                }
                itemsIndexed(remoteItems, key = { _, p -> "r-" + p.second.tmux }) { i, (c, r) ->
                    GroupRow(sessionList.size + i, sessionRows) {
                        RemoteSessionRow(
                            name = r.name ?: r.tmux,
                            tmux = r.tmux,
                            hostName = c.name,
                            otherDevice = r.device != null && r.device != sessions.deviceId,
                            enabled = repo.hasSavedSecret(c.id),
                            onAttach = { attachRemote(c, r) },
                        )
                    }
                }
            }

            // ── Agent etkinliği: son üç olay; gerisi "Tümü" ile.
            // Üstteki onay kartında duran olay burada tekrarlanmaz.
            val bannerEvent = pendingApprovals.firstOrNull()?.eventId
            val log = events.filter { it.eventId != bannerEvent }
            if (log.isNotEmpty()) {
                val shown = if (allEvents) log.take(24) else log.take(3)
                item(key = "sec-agents") {
                    SectionHeader(
                        "Agent etkinliği",
                        count = log.count { it.unread }.takeIf { it > 0 }?.let { "$it yeni" },
                        modifier = Modifier.padding(top = Space.xl),
                        action = if (log.size > 3) {
                            { GhostButton(if (allEvents) "Daralt" else "Tümü", onClick = { allEvents = !allEvents }, color = t.text2) }
                        } else null,
                    )
                }
                itemsIndexed(shown, key = { _, e -> "e-" + e.eventId }) { i, e ->
                    GroupRow(i, shown.size) {
                        val go = eventTarget(e)
                        AgentEventRow(e, canOpen = go != null) {
                            inbox?.markRead(e.eventId)
                            go?.invoke()
                        }
                    }
                }
            }

            // ── Hızlı başlat: yalnız profil varsa, tek satır.
            if (profileList.isNotEmpty()) {
                item(key = "sec-quick") { SectionHeader("Hızlı başlat", modifier = Modifier.padding(top = Space.xl)) }
                item(key = "quick") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        profileList.forEach { p ->
                            ProfilePill(
                                p,
                                hostName = saved.firstOrNull { it.id == p.connectionId }?.name,
                                onLaunch = { launchProfile(p) },
                                onMenu = { profileMenu = p },
                            )
                        }
                    }
                }
            }

            // ── Hostlar
            item(key = "sec-hosts") {
                SectionHeader("Hostlar", modifier = Modifier.padding(top = if (sessionRows > 0 || log.isNotEmpty() || profileList.isNotEmpty()) Space.xl else Space.lg))
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
                    // Çıkmaz sokak olmasın: aramayı temizle ya da yazılanı
                    // (kullanıcı@adres de olur) yeni host olarak ekle.
                    val q = query.trim()
                    Column(Modifier.padding(vertical = Space.sm, horizontal = 4.dp)) {
                        Text("\"$q\" ile eşleşen host yok.", style = MaterialTheme.typography.bodyMedium, color = t.text2)
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            GhostButton("Aramayı temizle", onClick = { query = "" }, color = t.text2)
                            GhostButton("Host olarak ekle", icon = Icons.Rounded.Add, onClick = {
                                val user = q.substringBefore('@', "")
                                val addr = q.substringAfter('@')
                                editor = NewHost.copy(name = addr, host = addr, user = user)
                                query = ""
                            })
                        }
                    }
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

    if (addMenu) {
        PocketSheet(onDismiss = { addMenu = false }, title = "Ekle") { dismiss ->
            SheetAction(Icons.Rounded.QrCodeScanner, "QR veya kodla eşle", sublabel = "Host'ta pocket-agent pair çalıştır", onClick = {
                dismiss(); openPair()
            })
            SheetAction(Icons.Rounded.Dns, "Host ekle", sublabel = "Adres, kullanıcı, parola ya da SSH anahtarı", onClick = {
                dismiss(); editor = NewHost
            })
            SheetAction(Icons.Rounded.FileOpen, "~/.ssh/config içe aktar", onClick = { dismiss(); openImport() })
            if (saved.isNotEmpty()) {
                SheetAction(Icons.Rounded.PlayArrow, "Hızlı başlat profili", sublabel = "Tek dokunuşla komut çalıştıran kısayol", onClick = {
                    dismiss(); profileEditor = NewProfile
                })
            }
        }
    }

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
        // Bu host'ta açık oturumlar ayrılır (host'taki tmux sürer): silinen
        // kaydın oturumu geri yüklenemez, "yeni oturum" da boşa düşerdi.
        val open = sessionList.filter { it.conn.id == c.id }
        ConfirmDialog(
            title = "${c.name} silinsin mi?",
            message = "Kayıtlı parola veya anahtar da bu cihazdan silinir. " +
                (if (open.isEmpty()) "" else "Bu cihazdaki ${open.size} oturumu kapanır; ") +
                "host'taki tmux oturumlarına dokunulmaz.",
            confirmLabel = "Sil",
            onConfirm = {
                open.forEach { sessions.close(it.id, kill = false) }
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
            onCode = { raw, code ->
                // Adres eşleşme başarılı olunca kaydedilir (adoptBackend);
                // yanlış yazılmış adres ayarları bozmaz.
                val (backend, urlError) = dev.pocketagent.net.normalizeBackendUrl(raw)
                val parsed = if (urlError == null) dev.pocketagent.net.PairingClient.parseQr(code, backend) else null
                when {
                    urlError != null -> pairError = urlError
                    parsed == null -> pairError = if (backend.isBlank()) "Kodla eşlemek için backend adresi gerekli." else "Kod 8 karakter olmalı (XXXX-XXXX)."
                    else -> {
                        pairError = null
                        startPair(parsed.first, parsed.second)
                    }
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
private fun HubHeader(onAdd: () -> Unit, onSettings: () -> Unit) {
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
        IconAction(Icons.Rounded.Add, "Ekle", onAdd)
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

// Kırpma çizimde yapılır (katmanda değil): köşeleri eşit olmayan bir
// şekille katman kırpması dokunmayı Path.op ile sınar; bu her dokunuşta
// yol kesişimi demek ve Robolectric'te dokunmaları tümden düşürüyordu.
@Composable
fun GroupRow(index: Int, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val r = 22.dp
    val shape: Shape = remember(index == 0, index == count - 1) {
        RoundedCornerShape(
            topStart = if (index == 0) r else 4.dp,
            topEnd = if (index == 0) r else 4.dp,
            bottomStart = if (index == count - 1) r else 4.dp,
            bottomEnd = if (index == count - 1) r else 4.dp,
        )
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .drawWithCache {
                val clip = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
                onDrawWithContent { clipPath(clip) { this@onDrawWithContent.drawContent() } }
            }
            .background(Tok.card),
    ) { content() }
}

// ── Oturum satırı ───────────────────────────────────────────────────────────
// Sade satır: durum noktası, ad, tek satır özet. Özet sırası: bağlantı
// sorunu (renk + metin), agent'ın son olayı, terminalin son dolu satırı.
// Dokun → terminal; uzun bas → yeniden adlandır; ⋮ → seçenekler.

@Composable
private fun SessionRow(
    h: SessionHandle,
    name: String,
    agent: InboxRow?,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMenu: () -> Unit,
) {
    val t = Tok
    val st by h.controller.state.collectAsState()
    val retry by h.controller.retryAttempt.collectAsState()
    val last by rememberPreviewLines(h, 1)
    val status = if (retry > 0) Status.Warn else st.status()
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onOpen, onRename, label = "Terminali aç", longLabel = "Yeniden adlandır")
            .heightIn(min = 64.dp)
            .padding(start = Space.lg, end = 4.dp, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status, Modifier.semantics { contentDescription = st.label(retry) })
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                st != ConnectionState.ACTIVE || retry > 0 -> Text(
                    st.label(retry),
                    style = MaterialTheme.typography.bodySmall,
                    color = status.tone().text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                agent != null -> Text(
                    listOf(agent.source.ifBlank { "agent" }, agentKind(agent.category).label).joinToString(" · ") + " — " + agent.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                else -> Text(
                    last.firstOrNull()?.text?.trim().orEmpty().ifEmpty { "Hazır" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalMonoFont.current),
                    color = t.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Ham terminal satırı ekran okuyucuya okunmaz.
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
        IconAction(Icons.Rounded.MoreVert, "$name seçenekleri", onMenu)
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
// Host'ta yaşayan ama bu cihazda açık olmayan tmux oturumu: aynı satır
// düzeni, sönük nokta. Parola kayıtlı değilse dokunuş düzenleyiciyi açar.
@Composable
private fun RemoteSessionRow(
    name: String,
    tmux: String,
    hostName: String,
    otherDevice: Boolean,
    enabled: Boolean,
    onAttach: () -> Unit,
) {
    val t = Tok
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onAttach, label = "Bağlan")
            .heightIn(min = 64.dp)
            .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(Status.Idle, Modifier.semantics { contentDescription = "bu cihazda açık değil" })
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = t.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull("host'ta çalışıyor", hostName, if (otherDevice) "başka cihaz" else null, tmux).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = t.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GhostButton(if (enabled) "Bağlan" else "Parola gir", onClick = onAttach, color = if (enabled) t.primary else t.text2)
    }
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

// Olay satırı: küçük tür ikonu, başlık, alt satırda tür · kaynak · zaman.
// Tür metin olarak da yazılı (renk tek başına anlam taşımaz).
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
            .heightIn(min = 56.dp)
            .padding(start = Space.lg, end = Space.md, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kind.icon, contentDescription = null, tint = tone.fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (e.unread) FontWeight.Medium else FontWeight.Normal),
                color = if (e.unread) t.text else t.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(kind.label, e.source.ifBlank { "agent" }, relativeTime(ts)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = t.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (e.unread) {
            Spacer(Modifier.width(Space.sm))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(t.primary).semantics { contentDescription = "okunmadı" })
        }
        // Agent'ın oturumu bulunduysa satır oraya götürür.
        if (canOpen) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = t.text3,
                modifier = Modifier.padding(start = Space.xs).size(20.dp),
            )
        }
    }
}

// ── Hızlı başlat ────────────────────────────────────────────────────────────

// Tek satırlık başlat hapı: ad görünür, bağlı host dokunma etiketinde.
// Uzun bas → profil seçenekleri.
@Composable
private fun ProfilePill(p: SavedProfile, hostName: String?, onLaunch: () -> Unit, onMenu: () -> Unit) {
    val t = Tok
    Row(
        Modifier
            .clip(PillShape)
            .background(t.card)
            .tap(onLaunch, onMenu, label = hostName?.let { "$it'ta başlat" } ?: "Başlat", longLabel = "Seçenekler")
            .heightIn(min = 48.dp)
            .padding(start = Space.md, end = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = t.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.sm))
        Text(p.name, style = MaterialTheme.typography.labelLarge, color = t.text, maxLines = 1)
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
        leading = { HostAvatar(conn.name) },
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
