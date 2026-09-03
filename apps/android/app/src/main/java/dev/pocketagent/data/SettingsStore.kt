// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// P10/P13: görünüm + backend bağlantı tercihleri DataStore'da kalıcı.
// tenantToken self-hosted iskelette X-Tenant header'ı olarak kullanılır;
// passkey geldiğinde access token ile değişecek (P02 tam dilim).
data class PersistedSettings(
    val dark: Boolean = true,
    val fontScale: Float = 1f,
    val backendUrl: String = "",
    val tenantToken: String = "",
    val amoled: Boolean = false,
)

interface SettingsStore {
    suspend fun load(): PersistedSettings?
    suspend fun save(s: PersistedSettings)
}

private val Context.prefs by preferencesDataStore(name = "settings")

class DataStoreSettingsStore(private val context: Context) : SettingsStore {
    private val darkKey = booleanPreferencesKey("dark")
    private val fontKey = floatPreferencesKey("font_scale")
    private val urlKey = stringPreferencesKey("backend_url")
    private val tenantKey = stringPreferencesKey("tenant_token")
    private val amoledKey = booleanPreferencesKey("amoled")

    override suspend fun load(): PersistedSettings? {
        val p = context.prefs.data.first()
        if (p[darkKey] == null && p[urlKey] == null) return null
        return PersistedSettings(
            dark = p[darkKey] ?: true,
            fontScale = p[fontKey] ?: 1f,
            backendUrl = p[urlKey] ?: "",
            tenantToken = p[tenantKey] ?: "",
            amoled = p[amoledKey] ?: false,
        )
    }

    override suspend fun save(s: PersistedSettings) {
        context.prefs.edit {
            it[darkKey] = s.dark
            it[fontKey] = s.fontScale
            it[urlKey] = s.backendUrl
            it[tenantKey] = s.tenantToken
            it[amoledKey] = s.amoled
        }
    }
}
