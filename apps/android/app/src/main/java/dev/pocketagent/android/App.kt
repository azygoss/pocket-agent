// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.android

import android.app.Application
import androidx.room.Room
import dev.pocketagent.data.AppDatabase
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.SshjConnector
import dev.pocketagent.transport.TerminalController
import dev.pocketagent.transport.TofuHostKeyStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Uygulama genelinde tek örnekler. Hepsi lazy: Robolectric altında ve
// process ölümü sonrası yeniden yaratımda güvenli.
class App : Application() {
    val appScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "pocket-agent.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val connections: ConnectionRepository by lazy { ConnectionRepository(db.connections()) }

    val hostKeys: TofuHostKeyStore by lazy { TofuHostKeyStore(File(filesDir, "known_hosts")) }

    val terminal: TerminalController by lazy {
        TerminalController(appScope, SshjConnector(hostKeys, appScope), hostKeys)
    }
}
