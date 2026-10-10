// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.android

import android.app.Application
import android.provider.Settings
import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.data.DataStoreSettingsStore
import dev.pocketagent.data.ProfileRepository
import dev.pocketagent.net.BackendClient
import dev.pocketagent.net.EventSync
import dev.pocketagent.security.KeystoreSecretStore
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshjConnector
import dev.pocketagent.transport.TofuHostKeyStore
import dev.pocketagent.ui.InboxViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val APPROVAL_NOTIFICATION = 1001

// Bildirilecek onaylar: inbox'ta henüz olmayan (uygulama yeniden açılınca
// 24 saatlik geçmiş tekrar bildirilmesin) ve son 10 dakikada oluşanlar.
internal fun approvalsToNotify(
    events: List<dev.pocketagent.net.BackendEvent>,
    known: Set<String>,
    nowMs: Long,
): List<dev.pocketagent.net.BackendEvent> = events.filter { e ->
    e.eventId !in known && e.category.contains("APPROVAL", ignoreCase = true) &&
        runCatching { java.time.Instant.parse(e.createdAt).toEpochMilli() }.getOrDefault(0L) >= nowMs - 10 * 60_000
}

// Uygulama genelinde tek örnekler. Hepsi lazy: Robolectric altında ve
// process ölümü sonrası yeniden yaratımda güvenli.
class App : Application() {
    val appScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "pocket-agent.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val settingsStore by lazy { DataStoreSettingsStore(this) }

    val secretStore by lazy { KeystoreSecretStore(File(filesDir, "secrets")) }

    val connections: ConnectionRepository by lazy { ConnectionRepository(db.connections(), secretStore) }

    // Hazır terminal profilleri (komut + opsiyonel host).
    val profiles: ProfileRepository by lazy { ProfileRepository(db.profiles()) }

    val hostKeys: TofuHostKeyStore by lazy { TofuHostKeyStore(File(filesDir, "known_hosts")) }

    val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    val sessions: SessionManager by lazy {
        SessionManager(appScope, hostKeys) { SshjConnector(hostKeys, appScope) }.apply {
            onConnected = { conn -> appScope.launch { connections.touch(conn.id) } }
            // Cihazlar-arası oturum keşfi: registry'nin d= alanı bununla
            // karşılaştırılır (başka cihazın oturumu → close'da detach-only).
            deviceId = this@App.deviceId
        }
    }

    // P13: agent inbox app-seviyesinde — EventSync poller'ı besler.
    val inbox by lazy { InboxViewModel(db.events(), appScope) }

    // P13 kullanım snapshot'ları (backend /v1/usages).
    val usage by lazy { dev.pocketagent.ui.UsageViewModel() }

    @Volatile var backendClient: BackendClient? = null
        private set

    val eventSync: EventSync by lazy {
        EventSync(
            appScope,
            { backendClient },
            onEvents = { events ->
                val known = inbox.rows.mapTo(HashSet()) { it.eventId }
                inbox.mergeRemote(events)
                notifyApprovals(approvalsToNotify(events, known, System.currentTimeMillis()))
            },
            onUsages = { arr -> usage.updateFrom(arr) },
        )
    }

    // Yeni onay isteği geldiğinde bildirim düşür; uygulama önündeyken Oturumlar
    // zaten onay bandını gösterir.
    private fun notifyApprovals(approvals: List<dev.pocketagent.net.BackendEvent>) {
        if (approvals.isEmpty()) return
        val proc = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(proc)
        if (proc.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(
            android.app.NotificationChannel("agents", "Agent onayları", android.app.NotificationManager.IMPORTANCE_HIGH),
        )
        val openApp = android.app.PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val last = approvals.last()
        val n = android.app.Notification.Builder(this, "agents")
            .setContentTitle(if (approvals.size == 1) "Agent onayı bekliyor" else "${approvals.size} agent onayı bekliyor")
            .setContentText(last.source.ifBlank { "agent" } + ": " + last.message.ifBlank { last.category })
            .setSmallIcon(dev.pocketagent.android.R.drawable.ic_stat_pocket)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        nm.notify(APPROVAL_NOTIFICATION, n)
    }

    // Onay uygulamada okununca bildirim gölgede kalmasın.
    fun clearApprovalNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(APPROVAL_NOTIFICATION)
    }

    fun configureBackend(url: String, tenant: String) {
        backendClient = if (url.isNotBlank() && tenant.isNotBlank()) BackendClient(url, tenant) else null
        // Ayar düzelince beklemeden dene (önceki hatanın 5dk geri çekilmesini kes).
        eventSync.syncNow()
    }

    // Bildirimdeki "Tümünü kapat" aksiyonu burada biter.
    private val closeAllReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == dev.pocketagent.service.TerminalService.ACTION_CLOSE_ALL) {
                sessions.closeAll()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            closeAllReceiver,
            android.content.IntentFilter(dev.pocketagent.service.TerminalService.ACTION_CLOSE_ALL),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
