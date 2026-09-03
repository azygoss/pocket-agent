// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// P15: uzak dosya erişimi. SFTP, zaten doğrulanmış SSH oturumunun içinden akar
// (ayrı auth yok, backend içerik görmez). SshjTransport bu arayüzü uygular;
// Fake transport uygulamaz → UI "SFTP yok" durumunu gösterir.
data class RemoteFile(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long, // epoch seconds
)

interface SftpSession {
    suspend fun home(): String
    suspend fun list(path: String): List<RemoteFile>
    // En fazla maxBytes okur (preview için); daha büyük dosyalar download'a.
    suspend fun readBytes(path: String, maxBytes: Long): ByteArray
    suspend fun writeBytes(path: String, data: ByteArray)
}
