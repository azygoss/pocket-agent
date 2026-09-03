// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// P10: tema/font tercihleri DataStore'da kalıcı — uygulama her açılışta
// sıfırlanmaz. Saf arayüz testlerde in-memory kalır.
interface SettingsStore {
    suspend fun load(): Pair<Boolean, Float>? // dark, fontScale
    suspend fun save(dark: Boolean, fontScale: Float)
}

private val Context.prefs by preferencesDataStore(name = "settings")

class DataStoreSettingsStore(private val context: Context) : SettingsStore {
    private val darkKey = booleanPreferencesKey("dark")
    private val fontKey = floatPreferencesKey("font_scale")

    override suspend fun load(): Pair<Boolean, Float>? {
        val p = context.prefs.data.first()
        val dark = p[darkKey] ?: return null
        return dark to (p[fontKey] ?: 1f)
    }

    override suspend fun save(dark: Boolean, fontScale: Float) {
        context.prefs.edit {
            it[darkKey] = dark
            it[fontKey] = fontScale
        }
    }
}
