// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

// P06/P09: active transports live in a foreground service; process death only
// re-attaches to multiplexer sessions, never re-runs commands.
class TerminalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("terminal", "Terminals", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        val n = Notification.Builder(this, "terminal")
            .setContentTitle("Pocket Agent")
            .setContentText("Terminal sessions active")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1, n)
    }
}
