// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

// P06/P09: aktif transport'lar foreground service'te yaşar; process death
// yalnız multiplexer (tmux) oturumlarına re-attach demektir, komutlar asla
// yeniden çalıştırılmaz. Bildirim aktif oturum sayısını gösterir.
class TerminalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("terminal", "Terminal oturumları", NotificationManager.IMPORTANCE_LOW),
        )
        startForeground(NOTIF_ID, buildNotification(1))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra(EXTRA_COUNT, -1) ?: -1
        if (count >= 0) {
            // Android 13+: izin yoksa bildirim güncellemesini sessizce atla
            // (FGS bildirimi onCreate'teki startForeground ile zaten zorunlu).
            val granted = android.os.Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification(count))
            }
        }
        return START_STICKY
    }

    private fun buildNotification(count: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // "Kapat" aksiyonu: tüm oturumları kapatır (App.closeAllReceiver'a gider).
        val closeAll = PendingIntent.getBroadcast(
            this, 1,
            Intent(ACTION_CLOSE_ALL).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, "terminal")
            .setContentTitle("Pocket Agent")
            .setContentText(if (count == 1) "1 aktif oturum" else "$count aktif oturum")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openApp)
            .addAction(
                Notification.Action.Builder(null, "Tümünü kapat", closeAll).build(),
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1
        const val EXTRA_COUNT = "count"
        const val ACTION_CLOSE_ALL = "dev.pocketagent.action.CLOSE_ALL"

        fun updateCount(context: Context, count: Int) {
            val i = Intent(context, TerminalService::class.java).putExtra(EXTRA_COUNT, count)
            context.startService(i)
        }
    }
}
