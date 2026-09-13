// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// GitHub Releases üzerinden uygulama içi güncelleme. Ayarlar'daki
// "Güncellemeleri kontrol et" akışı: fetchLatest → isNewer → download →
// installApk. Ağ ve JSON ayrık tutulur ki parse/compare saf test edilebilsin.
data class ReleaseInfo(
    val version: String, // "0.27.0" — v prefix'siz
    val apkUrl: String,
    val notes: String,
    val sizeBytes: Long,
)

object UpdateChecker {
    const val LATEST_URL =
        "https://api.github.com/repos/azygoss/pocket-agent/releases/latest"

    fun fetchLatest(url: String = LATEST_URL): ReleaseInfo? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "pocket-agent-android")
        try {
            if (c.responseCode != 200) return null
            return parseRelease(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    // releases/latest JSON'u → ilk .apk asset'i. APK'sız release → null.
    fun parseRelease(body: String): ReleaseInfo? {
        val o = JSONObject(body)
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return ReleaseInfo(
                    version = o.optString("tag_name").removePrefix("v"),
                    apkUrl = a.getString("browser_download_url"),
                    notes = o.optString("body"),
                    sizeBytes = a.optLong("size"),
                )
            }
        }
        return null
    }

    // "v0.27.1" vs "0.27.0" → sayısal segment karşılaştırması (suffix'ler
    // atılır: "0.27.0-rc1" → [0,27,0]).
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-', '+')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // APK'yı FileProvider'ın sunduğu shared/ önbelleğine indirir.
    fun download(url: String, dest: File, onProgress: (Float) -> Unit = {}) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true // GitHub asset → signed CDN URL
        c.setRequestProperty("User-Agent", "pocket-agent-android")
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: -1L
            dest.parentFile?.mkdirs()
            c.inputStream.use { ins ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    // Paket kurucuyu açar; Android 8+ "bilinmeyen uygulama kur" izni
    // verilmediyse önce o ayar sayfasına gider.
    fun installApk(context: Context, file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
