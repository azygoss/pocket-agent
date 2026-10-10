// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import dev.pocketagent.android.App
import dev.pocketagent.service.TerminalService
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.ui.theme.Motion
import dev.pocketagent.ui.theme.PocketAgentTheme
import dev.pocketagent.ui.theme.Space
import dev.pocketagent.ui.theme.consoleFont
import dev.pocketagent.ui.theme.consoleTheme
import dev.pocketagent.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow

// ── Gezinme ─────────────────────────────────────────────────────────────────
// Sekme çubuğu yok. Kök ekran Oturumlar; terminal oturuma dokununca alttan
// gelen bir katman, dosyalar ve ayarlar sağdan itilen sayfalar. Geri her
// zaman bir önceki katmana döner; oturumlar arka planda yaşar.
enum class Route { Hub, Workspace, Files, Settings }

@Composable
fun PocketAgentApp(
    app: App,
    deepLinkAction: MutableStateFlow<String?> = MutableStateFlow(null),
    addHostLink: MutableStateFlow<dev.pocketagent.transport.SavedConnection?> = MutableStateFlow(null),
) {
    val settings = remember { SettingsViewModel(app.settingsStore, app.appScope) }
    val usage = app.usage
    val files = remember { FilesViewModel(app.sessions, app.appScope, app.cacheDir) }
    val stack = remember { mutableStateListOf(Route.Hub) }
    fun push(r: Route) { if (stack.last() != r) stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val top = stack.last()
    val termChrome = remember { TerminalChromeState() }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    val sessions = app.sessions
    val connections2 = app.connections.items.collectAsState()
    val hostKeyPrompt by sessions.hostKeyPrompt.collectAsState()
    val anyActive by sessions.anyActive.collectAsState()

    LaunchedEffect(Unit) { app.connections.refresh(); app.profiles.refresh() }

    // Açılışta otomatik reconnect (opt-in): yalnız Keystore'da secret saklıysa.
    var autoReconnectTried by remember { mutableStateOf(false) }
    // Kopmada yeniden bağlanma tercihini oturum yöneticisine yansıt.
    LaunchedEffect(settings.autoReconnectOnDrop) { sessions.autoReconnectOnDrop = settings.autoReconnectOnDrop }
    LaunchedEffect(settings.autoReconnect, connections2.value) {
        if (!settings.autoReconnect || autoReconnectTried) return@LaunchedEffect
        val conns = connections2.value
        if (conns.isEmpty() || sessions.sessions.value.isNotEmpty()) return@LaunchedEffect
        autoReconnectTried = true
        val last = conns.maxByOrNull { it.lastConnectedAt } ?: return@LaunchedEffect
        val secret = app.connections.secret(last.id) ?: return@LaunchedEffect // RAM/Keystore'da yoksa elle bağlan
        sessions.open(last, secret)
        push(Route.Workspace)
    }

    // Oturum persistansı: açık oturumların conn id'leri diske yazılır;
    // açılışta kullanıcı kapatmadıysa geri yüklenip yeniden bağlanır
    // (uzak tarafta tmux re-attach kaldığı yerden devam ettirir).
    var sessionsRestored by remember { mutableStateOf(false) }
    LaunchedEffect(connections2.value) {
        if (sessionsRestored) return@LaunchedEffect
        val conns = connections2.value
        if (conns.isEmpty()) return@LaunchedEffect
        sessionsRestored = true
        app.settingsStore.loadOpenSessions().forEach { os ->
            conns.firstOrNull { it.id == os.connId }?.let { c ->
                app.connections.secret(c.id)?.let { s ->
                    // Aynı host'ta birden çok kayıtlı oturum → paralel aç;
                    // kayıtlı tmux adı aynı uzak oturuma reattach eder.
                    sessions.open(
                        c, s,
                        forceNew = sessions.sessions.value.any { it.conn.id == c.id },
                        customName = os.name,
                        tmuxName = os.tmux,
                    )
                }
            }
        }
    }
    // Liste yalnız restore denendikten sonra diske yazılır — aksi halde ilk
    // boş emission kayıtlı id'leri okunmadan silerdi.
    LaunchedEffect(sessionsRestored) {
        if (!sessionsRestored) return@LaunchedEffect
        kotlinx.coroutines.flow.combine(
            sessions.sessions,
            sessions.customNames,
            sessions.tmuxNames,
        ) { list, names, tmuxes ->
            list.map { dev.pocketagent.data.OpenSession(it.conn.id, names[it.id], tmuxes[it.id]) }
        }.collect { app.settingsStore.saveOpenSessions(it) }
    }

    // pocketagent://tmux|herdr → açık oturum varsa terminale geç.
    LaunchedEffect(Unit) {
        deepLinkAction.collect { action ->
            if (action != null) {
                if (sessions.sessions.value.isNotEmpty()) { home(); push(Route.Workspace) }
                deepLinkAction.value = null
            }
        }
    }
    // pocketagent://add?host=… ya da terminalden "hostu düzenle" → Oturumlar'da
    // düzenleyici açılır (Hub akışı tüketir).
    LaunchedEffect(Unit) {
        addHostLink.collect { c -> if (c != null) home() }
    }

    // Çalışma alanından çıkınca tam ekran/arama sıfırlanır (sistem çubukları döner).
    LaunchedEffect(top) {
        if (top != Route.Workspace) { termChrome.fullscreen = false; termChrome.searchOpen = false }
    }

    // Son oturum kapanınca terminal/dosyalar çıkmaz sokağa dönmesin.
    val sessionList by sessions.sessions.collectAsState()
    LaunchedEffect(sessionList.isEmpty()) {
        if (sessionList.isEmpty()) stack.removeAll { it == Route.Workspace || it == Route.Files }
    }

    // Backend ayarı değişince client'ı yeniden kur, poller'ı başlat.
    LaunchedEffect(settings.backendUrl, settings.tenantToken) {
        app.configureBackend(settings.backendUrl, settings.tenantToken)
        if (settings.backendConfigured) app.eventSync.start() else app.eventSync.stop()
    }

    // Oturum varken foreground service ayakta; yokken durur.
    val activeCount = sessionList.count { it.controller.state.collectAsState().value == ConnectionState.ACTIVE }
    LaunchedEffect(activeCount) {
        if (activeCount > 0) {
            // Android 13+: bildirim izni yoksa FGS bildirimi görünmez — iste.
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            ContextCompat.startForegroundService(context, Intent(context, TerminalService::class.java))
            TerminalService.updateCount(context, activeCount)
        } else if (!anyActive) {
            context.stopService(Intent(context, TerminalService::class.java))
        }
    }
    LaunchedEffect(anyActive) {
        if (anyActive) snackbar.showSnackbar("Oturum bağlandı")
    }

    androidx.activity.compose.BackHandler(enabled = stack.size > 1) { pop() }

    val console = consoleTheme(settings.theme.themeId)
    // Sistem çubuğu ikon kontrastı temayı takip eder — açık temada koyu ikon.
    val barView = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(console.dark) {
        val w = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(w, barView).apply {
            isAppearanceLightStatusBars = !console.dark
            isAppearanceLightNavigationBars = !console.dark
        }
    }
    // Tercihler okunana kadar yalnız pencere zemini (splash ile aynı renk).
    if (!settings.loaded) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0F1317)))
        return
    }
    PocketAgentTheme(theme = console, mono = consoleFont(settings.theme.fontId).family) {
        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            val t = Tok
            val syncStatus by app.eventSync.status.collectAsState()
            val wide = LocalConfiguration.current.screenWidthDp >= 840
            val holder = rememberSaveableStateHolder()

            @Composable
            fun Screen(route: Route, twoPane: Boolean) {
                holder.SaveableStateProvider(route.name) {
                    when (route) {
                        Route.Hub -> HubScreen(
                            sessions = sessions,
                            repo = app.connections,
                            profiles = app.profiles,
                            inbox = app.inbox,
                            syncStatus = if (settings.backendConfigured) syncStatus else null,
                            app = app,
                            settings = settings,
                            pendingAdd = addHostLink,
                            showDock = !twoPane,
                            onOpenSession = { home(); push(Route.Workspace) },
                            onOpenFiles = { home(); push(Route.Files) },
                            onOpenSettings = { home(); push(Route.Settings) },
                        )
                        Route.Workspace -> TerminalScreen(
                            manager = sessions,
                            settings = settings,
                            onNewConnection = { home() },
                            onCollapse = { pop() },
                            chrome = termChrome,
                            onOpenFiles = { push(Route.Files) },
                            repo = app.connections,
                            onEditHost = { c -> addHostLink.value = c; home() },
                            showBack = !twoPane,
                        )
                        Route.Files -> FilesScreen(
                            files = files,
                            onOpenTerminal = { stack.removeAll { it == Route.Files }; push(Route.Workspace) },
                            onBack = { pop() },
                        )
                        Route.Settings -> SettingsScreen(
                            settings = settings,
                            usage = usage,
                            hostKeys = app.hostKeys,
                            app = app,
                            onBack = { pop() },
                        )
                    }
                }
            }

            val reduced = rememberReducedMotion()
            Box(Modifier.fillMaxSize().background(if (top == Route.Workspace) t.termBg else t.bg)) {
                if (wide) {
                    // Geniş pencere: solda Oturumlar sabit, sağda seçili katman.
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(400.dp).fillMaxHeight()) { Screen(Route.Hub, twoPane = true) }
                        VerticalDivider(color = t.line)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (top == Route.Hub) {
                                DetailPlaceholder()
                            } else {
                                AnimatedContent(
                                    targetState = top,
                                    transitionSpec = {
                                        if (reduced) EnterTransition.None togetherWith ExitTransition.None
                                        else fadeIn(tween(Motion.Standard)) togetherWith fadeOut(tween(Motion.Fast))
                                    },
                                    label = "detail",
                                ) { r -> Screen(r, twoPane = true) }
                            }
                        }
                    }
                } else {
                    val depth = stack.size
                    AnimatedContent(
                        targetState = top to depth,
                        contentKey = { it.first },
                        transitionSpec = {
                            val (from, fromDepth) = initialState
                            val (to, toDepth) = targetState
                            val forward = toDepth > fromDepth
                            val vertical = (from == Route.Hub && to == Route.Workspace) || (from == Route.Workspace && to == Route.Hub)
                            when {
                                reduced -> EnterTransition.None togetherWith ExitTransition.None
                                vertical && forward ->
                                    (slideInVertically(tween(Motion.Emphasized, easing = Motion.Enter)) { it / 5 } + fadeIn(tween(Motion.Standard))) togetherWith
                                        fadeOut(tween(Motion.Fast))
                                vertical ->
                                    fadeIn(tween(Motion.Standard)) togetherWith
                                        (slideOutVertically(tween(Motion.Standard, easing = Motion.Exit)) { it / 4 } + fadeOut(tween(Motion.Standard)))
                                forward ->
                                    (slideInHorizontally(tween(Motion.Emphasized, easing = Motion.Enter)) { it / 8 } + fadeIn(tween(Motion.Standard))) togetherWith
                                        (slideOutHorizontally(tween(Motion.Fast, easing = Motion.Exit)) { -it / 16 } + fadeOut(tween(Motion.Fast)))
                                else ->
                                    (slideInHorizontally(tween(Motion.Emphasized, easing = Motion.Enter)) { -it / 16 } + fadeIn(tween(Motion.Standard))) togetherWith
                                        (slideOutHorizontally(tween(Motion.Fast, easing = Motion.Exit)) { it / 8 } + fadeOut(tween(Motion.Fast)))
                            }
                        },
                        label = "route",
                    ) { (r, _) -> Screen(r, twoPane = false) }
                }
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = if (top == Route.Workspace) 72.dp else 0.dp),
                ) { PocketSnackbar(it) }
            }

            // TOFU: ilk bağlantıda parmak izi onayı; değişim zaten hard-stop.
            hostKeyPrompt?.let { prompt ->
                val key = prompt.key
                PocketDialog(
                    onDismiss = { prompt.controller.rejectHostKey() },
                    title = "Bu host'a güveniyor musun?",
                    icon = Icons.Rounded.GppMaybe,
                    iconTone = t.warn,
                    content = {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                            Text(
                                "${key.host}:${key.port} ilk kez görülüyor. Parmak izini host'takiyle karşılaştır; pinledikten sonra anahtar değişirse bağlantı durur.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ValueBox(key.fingerprint, label = key.algorithm)
                            Text(
                                "Host'ta kontrol: ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = dev.pocketagent.ui.theme.Code),
                                color = t.text2,
                            )
                        }
                    },
                    confirm = { GhostButton("Pinle ve bağlan", onClick = { prompt.controller.acceptHostKeyAndReconnect() }) },
                    dismiss = { GhostButton("Vazgeç", onClick = { prompt.controller.rejectHostKey() }, color = t.text2) },
                )
            }
        }
    }
}

// İki bölmeli düzende sağ taraf boşken: ne yapılacağını söyleyen sakin yüzey.
@Composable
private fun DetailPlaceholder() {
    Column(
        Modifier.fillMaxSize().padding(Space.x3),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(size = 56.dp)
        Spacer(Modifier.height(Space.lg))
        Text("Bir oturum seç", style = MaterialTheme.typography.titleLarge, color = Tok.text)
        Spacer(Modifier.height(Space.sm))
        Text(
            "Soldaki listeden bir oturuma ya da host'a dokun; terminal burada açılır.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tok.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.6f),
        )
    }
}
