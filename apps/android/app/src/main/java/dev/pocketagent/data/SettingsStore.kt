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
    val fontScale: Float = 1f,
    val backendUrl: String = "",
    val tenantToken: String = "",
    val autoReconnect: Boolean = false,
    // Kopmada otomatik yeniden bağlanma (SSH) — varsayılan açık; auth/host-key
    // hataları P08 gereği yine hard-stop.
    val autoReconnectOnDrop: Boolean = true,
    // Görünüm: tema kataloğu kimliği + terminal font kimliği.
    val themeId: String = "pocket",
    val fontId: String = "jetbrains",
    // Tuş şeridi snippet'ları: her satır "etiket=komut" (örn. "gs=git status").
    val snippets: String = "",
)

interface SettingsStore {
    suspend fun load(): PersistedSettings?
    suspend fun save(s: PersistedSettings)
}

private val Context.prefs by preferencesDataStore(name = "settings")

class DataStoreSettingsStore(private val context: Context) : SettingsStore {
    private val fontKey = floatPreferencesKey("font_scale")
    private val urlKey = stringPreferencesKey("backend_url")
    private val tenantKey = stringPreferencesKey("tenant_token")
    private val autoReconnectKey = booleanPreferencesKey("auto_reconnect")
    private val autoReconnectDropKey = booleanPreferencesKey("auto_reconnect_drop")
    private val themeIdKey = stringPreferencesKey("theme_id")
    private val fontIdKey = stringPreferencesKey("font_id")
    private val snippetsKey = stringPreferencesKey("snippets")

    override suspend fun load(): PersistedSettings? {
        val p = context.prefs.data.first()
        if (p[themeIdKey] == null && p[urlKey] == null) return null
        return PersistedSettings(
            fontScale = p[fontKey] ?: 1f,
            backendUrl = p[urlKey] ?: "",
            tenantToken = p[tenantKey] ?: "",
            autoReconnect = p[autoReconnectKey] ?: false,
            autoReconnectOnDrop = p[autoReconnectDropKey] ?: true,
            themeId = p[themeIdKey] ?: "pocket",
            fontId = p[fontIdKey] ?: "jetbrains",
            snippets = p[snippetsKey] ?: "",
        )
    }

    override suspend fun save(s: PersistedSettings) {
        context.prefs.edit {
            it[fontKey] = s.fontScale
            it[urlKey] = s.backendUrl
            it[tenantKey] = s.tenantToken
            it[autoReconnectKey] = s.autoReconnect
            it[autoReconnectDropKey] = s.autoReconnectOnDrop
            it[themeIdKey] = s.themeId
            it[fontIdKey] = s.fontId
            it[snippetsKey] = s.snippets
        }
    }
}
