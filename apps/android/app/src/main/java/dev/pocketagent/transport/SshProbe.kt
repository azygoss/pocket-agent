// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException

// "Bağlantıyı sına": kaydetmeden önce ağ + SSH auth'u probe eder.
// TOFU deposuna DOKUNMAZ — pin akışı gerçek bağlantıda kalır; bu yalnız
// tanı amaçlı promiscuous bağlantıdır (sonuç kullanıcıya gösterilir).
object SshProbe {
    sealed interface Result {
        data object Ok : Result
        data class AuthRejected(val detail: String) : Result
        data class Net(val detail: String) : Result
    }

    suspend fun run(conn: SavedConnection, secret: Secret?, timeoutMs: Int = 6_000): Result =
        withContext(Dispatchers.IO) {
            SshjConnector.ensureAndroidCryptoProvider()
            val ssh = SSHClient()
            ssh.addHostKeyVerifier(PromiscuousVerifier())
            ssh.connectTimeout = timeoutMs
            ssh.timeout = timeoutMs
            try {
                ssh.connect(conn.host, conn.port)
                when (secret) {
                    is Secret.Password -> ssh.authPassword(conn.user, secret.value)
                    is Secret.PemKey -> {
                        val tmp = java.io.File.createTempFile("pa-probe", ".pem")
                        try {
                            tmp.writeText(secret.pem)
                            val kp = if (secret.passphrase.isNullOrEmpty()) ssh.loadKeys(tmp.absolutePath)
                            else ssh.loadKeys(tmp.absolutePath, secret.passphrase.toCharArray())
                            ssh.authPublickey(conn.user, kp)
                        } finally {
                            tmp.delete()
                        }
                    }
                    null -> return@withContext Result.AuthRejected("secret girilmedi")
                }
                Result.Ok
            } catch (e: UserAuthException) {
                Result.AuthRejected("auth reddedildi (parola/anahtar?)")
            } catch (e: Exception) {
                Result.Net(e.message ?: e.javaClass.simpleName)
            } finally {
                runCatching { ssh.disconnect() }
            }
        }
}
