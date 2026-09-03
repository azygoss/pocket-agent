// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import android.content.Context
import java.io.File

// P07: mosh-client, jniLibs içinde libmoshclient.so olarak paketlenir;
// Android 10+ W^X nedeniyle yalnızca nativeLibraryDir'den exec edilebilir.
// Bootstrap akışı: SSH ile `mosh-server new` çalıştır → anahtar SSH içinde
// gelir (diske düşmez) → mosh-client UDP'ye bağlanır. Bu sınıf yalnızca
// binary'nin varlığını ve yolunu çözer; roaming davranışı cihazda doğrulanır.
class MoshRuntime(private val context: Context) {
    fun clientPath(): String =
        File(context.applicationInfo.nativeLibraryDir, "libmoshclient.so").absolutePath

    fun isBundled(): Boolean = File(clientPath()).exists()

    // Terminfo veritabanı assets'ten cache'e açılır (mosh-client TERMINFO ister).
    fun ensureTerminfo(): File {
        val dir = File(context.cacheDir, "terminfo")
        val marker = File(dir, ".unpacked")
        if (!marker.exists()) {
            dir.mkdirs()
            context.assets.open("terminfo.zip").use { input ->
                java.util.zip.ZipInputStream(input).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val out = File(dir, e.name)
                        if (e.isDirectory) out.mkdirs()
                        else {
                            out.parentFile?.mkdirs()
                            out.writeBytes(z.readBytes())
                        }
                        e = z.nextEntry
                    }
                }
            }
            marker.writeText("ok")
        }
        return dir
    }
}
