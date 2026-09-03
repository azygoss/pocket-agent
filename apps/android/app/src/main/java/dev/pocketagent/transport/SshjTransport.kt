// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import java.io.File
import java.security.PublicKey
import java.security.Security

// P06: real SSH transport over SSHJ behind the same SshTransport surface the
// fake implements. Auth failures and host-key mismatches hard-stop — the
// controller never falls back to another transport on those (P08 rule).

interface SshConnector {
    suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport
}

private class TofuVerifier(
    private val store: TofuHostKeyStore,
    private val host: String,
    private val port: Int,
) : HostKeyVerifier {
    @Volatile var presented: PresentedKey? = null
        private set

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val p = PresentedKey(host, this.port, key.algorithm, key.encoded)
        presented = p
        val pinned = store.lookup(host, this.port) ?: return false
        return pinned.sameKeyAs(p)
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
        val pinned = store.lookup(host, this.port) ?: return emptyList()
        return listOf(algorithmName(pinned.algorithm))
    }

    private fun algorithmName(jca: String): String = when (jca) {
        "RSA" -> "ssh-rsa,rsa-sha2-512,rsa-sha2-256"
        "EC", "ECDSA" -> "ecdsa-sha2-nistp256"
        "EdDSA", "Ed25519" -> "ssh-ed25519"
        "DSA" -> "ssh-dss"
        else -> jca
    }
}

class SshjConnector(
    private val hostKeys: TofuHostKeyStore,
    private val readScope: CoroutineScope,
    private val connectTimeoutMs: Int = 10_000,
) : SshConnector {

    override suspend fun open(conn: SavedConnection, secret: Secret?, size: TerminalSize): SshTransport =
        withContext(Dispatchers.IO) {
            ensureAndroidCryptoProvider()
            val ssh = SSHClient()
            val verifier = TofuVerifier(hostKeys, conn.host, conn.port)
            ssh.addHostKeyVerifier(verifier)
            ssh.connectTimeout = connectTimeoutMs
            ssh.timeout = connectTimeoutMs
            try {
                ssh.connect(conn.host, conn.port)
            } catch (e: TransportException) {
                ssh.disconnectQuietly()
                if (e.disconnectReason == DisconnectReason.HOST_KEY_NOT_VERIFIABLE) {
                    val p = verifier.presented
                        ?: throw TransportFailureException(TransportFailure.Network("host key not presented"))
                    val pinned = hostKeys.lookup(conn.host, conn.port)
                    if (pinned != null) throw HostKeyChangedException(p, pinned)
                    throw UnknownHostKeyException(p)
                }
                throw e
            }
            try {
                authenticate(ssh, conn, secret)
            } catch (e: Exception) {
                ssh.disconnectQuietly()
                throw e
            }
            val session = ssh.startSession()
            session.allocatePTY("xterm-256color", size.cols, size.rows, 0, 0, emptyMap())
            val shell = session.startShell()
            // 30s heartbeat: NAT/boşta kesilmelere karşı bağlantı canlı kalır.
            runCatching { ssh.connection.keepAlive.keepAliveInterval = 30 }
            SshjTransport(ssh, session, shell, size, readScope).also { it.startReader() }
        }

    private fun authenticate(ssh: SSHClient, conn: SavedConnection, secret: Secret?) {
        try {
            when (secret) {
                is Secret.Password -> ssh.authPassword(conn.user, secret.value)
                is Secret.PemKey -> {
                    // SSHJ KeyProvider'ı dosyayı auth anında (tembel) okur —
                    // temp dosya auth bitmeden silinmemeli.
                    val tmp = writeTempPem(secret.pem)
                    try {
                        val kp = if (secret.passphrase.isNullOrEmpty()) ssh.loadKeys(tmp.absolutePath)
                        else ssh.loadKeys(tmp.absolutePath, secret.passphrase.toCharArray())
                        ssh.authPublickey(conn.user, kp)
                    } finally {
                        tmp.delete()
                    }
                }
                null -> throw AuthFailedException()
            }
        } catch (e: UserAuthException) {
            throw AuthFailedException(e)
        }
    }

    // PEM RAM-only taşınır; SSHJ dosyadan okuduğu için 0600 temp dosyaya
    // yazılır ve auth sonrası hemen silinir.
    private fun writeTempPem(pem: String): File {
        val tmp = File.createTempFile("pa-key", ".pem")
        tmp.setReadable(false, false); tmp.setReadable(true, true)
        tmp.setWritable(false, false); tmp.setWritable(true, true)
        tmp.writeText(pem)
        return tmp
    }

    private fun SSHClient.disconnectQuietly() {
        runCatching { disconnect() }
    }

    companion object {
        @Volatile private var providerReady = false

        // Android ships a cut-down "BC" provider missing algorithms SSHJ
        // needs (Ed25519, ChaCha20). Replace it with full BouncyCastle.
        @Synchronized
        fun ensureAndroidCryptoProvider() {
            if (providerReady) return
            runCatching {
                Security.removeProvider("BC")
                Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
            }
            providerReady = true
        }
    }
}

class TransportFailureException(val failure: TransportFailure) : Exception(failure.toString())

class SshjTransport(
    private val ssh: SSHClient,
    private val session: Session,
    private val shell: Session.Shell,
    private var size: TerminalSize,
    private val readScope: CoroutineScope,
) : SshTransport {
    override val transport = TerminalTransport.SSH
    private val chan = Channel<TerminalFrame>(Channel.UNLIMITED)
    @Volatile private var closed = false

    fun startReader() {
        readScope.launch(Dispatchers.IO) {
            val buf = ByteArray(32768)
            try {
                while (!closed) {
                    val n = shell.inputStream.read(buf)
                    if (n < 0) break
                    if (n > 0) chan.trySend(TerminalFrame(buf.copyOf(n), transport))
                }
            } catch (_: Throwable) {
            } finally {
                chan.close()
            }
        }
    }

    // PTY is allocated by the connector before startShell; a later call with
    // a different size is treated as a window resize.
    override suspend fun openPty(term: String, size: TerminalSize) {
        if (size != this.size) resize(size)
    }

    override suspend fun send(input: TerminalInput) = withContext(Dispatchers.IO) {
        check(!closed) { "transport closed" }
        when (input) {
            is TerminalInput.Text -> shell.outputStream.write(input.s.toByteArray(Charsets.UTF_8))
            is TerminalInput.Key -> shell.outputStream.write(input.code)
            is TerminalInput.Resize -> {
                resize(input.size)
                return@withContext
            }
        }
        shell.outputStream.flush()
    }

    override suspend fun read(): TerminalFrame = chan.receive()

    override fun poll(): TerminalFrame? = chan.tryReceive().getOrNull()

    override suspend fun resize(size: TerminalSize) {
        withContext(Dispatchers.IO) {
            this@SshjTransport.size = size
            // changeWindowDimensions is on the channel implementation, not the Session interface
            (session as? net.schmizz.sshj.connection.channel.direct.SessionChannel)
                ?.changeWindowDimensions(size.cols, size.rows, 0, 0)
        }
    }

    override fun close() {
        closed = true
        runCatching { session.close() }
        runCatching { ssh.disconnect() }
        chan.close()
    }
}
