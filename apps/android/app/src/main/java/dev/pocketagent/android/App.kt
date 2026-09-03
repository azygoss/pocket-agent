// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.android

import android.app.Application
import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.data.DataStoreSettingsStore
import dev.pocketagent.security.KeystoreSecretStore
import dev.pocketagent.transport.SessionManager
import dev.pocketagent.transport.SshjConnector
import dev.pocketagent.transport.TofuHostKeyStore
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

    val sessions: SessionManager by lazy {
        SessionManager(appScope, hostKeys) { SshjConnector(hostKeys, appScope) }.apply {
            onConnected = { conn -> appScope.launch { connections.touch(conn.id) } }
        }
    }
}
