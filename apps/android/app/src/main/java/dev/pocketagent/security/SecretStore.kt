// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.pocketagent.transport.Secret
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// P05: secret'lar ya RAM'de (repository) ya da burada — Android Keystore'daki
// AES-256-GCM anahtarıyla şifreli dosyada. Plaintext asla diske düşmez.
interface SecretStore {
    fun save(profileId: String, secret: Secret): Boolean
    fun load(profileId: String): Secret?
    fun delete(profileId: String)
    fun has(profileId: String): Boolean
}

// Serileştirme: saf JVM, ayrı test edilir.
object SecretCodec {
    fun encode(s: Secret): ByteArray = when (s) {
        is Secret.Password -> "P\u0001${s.value}".toByteArray(Charsets.UTF_8)
        is Secret.PemKey -> "K\u0001${s.pem}\u0001${s.passphrase ?: ""}".toByteArray(Charsets.UTF_8)
    }

    fun decode(b: ByteArray): Secret? {
        val s = String(b, Charsets.UTF_8)
        if (s.length < 2 || s[1] != '\u0001') return null
        return when (s[0]) {
            'P' -> Secret.Password(s.substring(2))
            'K' -> {
                val rest = s.substring(2)
                val sep = rest.lastIndexOf('\u0001')
                if (sep < 0) return null
                Secret.PemKey(rest.substring(0, sep), rest.substring(sep + 1).ifEmpty { null })
            }
            else -> null
        }
    }
}

class KeystoreSecretStore(private val dir: File) : SecretStore {
    private val keyAlias = "pocket-agent-secret-v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return kg.generateKey()
    }

    private fun fileFor(id: String) = File(dir, "sec-" + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(id.toByteArray()) + ".bin")

    override fun save(profileId: String, secret: Secret): Boolean = runCatching {
        dir.mkdirs()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(SecretCodec.encode(secret))
        // iv | cipher
        fileFor(profileId).writeBytes(c.iv + enc)
        true
    }.getOrDefault(false)

    override fun load(profileId: String): Secret? = runCatching {
        val raw = fileFor(profileId).readBytes()
        if (raw.size < 13) return null
        val iv = raw.copyOfRange(0, 12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        SecretCodec.decode(c.doFinal(raw.copyOfRange(12, raw.size)))
    }.getOrNull()

    override fun delete(profileId: String) {
        fileFor(profileId).delete()
    }

    override fun has(profileId: String): Boolean = fileFor(profileId).exists()
}
