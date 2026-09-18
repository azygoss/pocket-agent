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
    // Dizini üstleriyle birlikte oluşturur (mkdir -p; varsa no-op).
    suspend fun mkdir(path: String)
    // Dosya/symlink SFTP rm; dizin exec rm -rf (rmdir yalnız boş dizini siler).
    suspend fun delete(path: String, isDir: Boolean)
    suspend fun rename(from: String, to: String)
}

// POSIX tek-tırnak quoting: exec komutlarına ve PTY'ye yazılan uzak yollarda
// boşluk/özel karakter güvenliği için tek nokta.
fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
