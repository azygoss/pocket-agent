// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.android

import android.app.Application
import android.provider.Settings
import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.data.DataStoreSettingsStore
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

    val hostKeys: TofuHostKeyStore by lazy { TofuHostKeyStore(File(filesDir, "known_hosts")) }

    val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    val sessions: SessionManager by lazy {
        SessionManager(appScope, hostKeys) { SshjConnector(hostKeys, appScope) }.apply {
            onConnected = { conn -> appScope.launch { connections.touch(conn.id) } }
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
                inbox.mergeRemote(events)
                notifyApprovals(events)
            },
            onUsages = { arr -> usage.updateFrom(arr) },
        )
    }

    // Yeni onay isteği geldiğinde (uygulama arka plandayken) bildirim düşür.
    private fun notifyApprovals(events: List<dev.pocketagent.net.BackendEvent>) {
        val approvals = events.filter { it.category.contains("APPROVAL", ignoreCase = true) }
        if (approvals.isEmpty()) return
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
        val n = android.app.Notification.Builder(this, "agents")
            .setContentTitle("Agent onayı bekliyor")
            .setContentText(approvals.last().message.ifBlank { approvals.last().category })
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
    }

    fun configureBackend(url: String, tenant: String) {
        backendClient = if (url.isNotBlank() && tenant.isNotBlank()) BackendClient(url, tenant) else null
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
