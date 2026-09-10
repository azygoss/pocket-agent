// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.security

import dev.pocketagent.transport.SshjConnector
import java.security.KeyPairGenerator
import java.util.Base64

// Uygulama içi ed25519 anahtar üretimi (BouncyCastle — SSHJ ile gelen tam
// provider). Private key PKCS8 PEM olarak döner (SSHJ loadKeys yükler);
// public key OpenSSH "ssh-ed25519 AAAA…" formatındadır.
// Not: java.security EdEC arayüzleri API 33 ister; burada yalnızca
// encoded baytlar kullanılır (minSdk 29 uyumlu).
object KeyGen {
    data class Generated(val privatePem: String, val publicOpenSsh: String)

    fun ed25519(comment: String = "pocket-agent"): Generated {
        SshjConnector.ensureAndroidCryptoProvider()
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        return Generated(
            privatePem = pem("PRIVATE KEY", kp.private.encoded),
            publicOpenSsh = "ssh-ed25519 " + b64(openSshPub(kp.public.encoded)) + " " + comment,
        )
    }

    // OpenSSH public blob: string "ssh-ed25519" + string raw-pubkey.
    // X.509 SubjectPublicKeyInfo'nun son 32 baytı ham anahtardır.
    private fun openSshPub(x509: ByteArray): ByteArray {
        val raw = x509.takeLast(32).toByteArray()
        val out = java.io.ByteArrayOutputStream()
        wstr(out, "ssh-ed25519".toByteArray()); wstr(out, raw)
        return out.toByteArray()
    }

    private fun wstr(out: java.io.ByteArrayOutputStream, b: ByteArray) {
        out.write((b.size shr 24) and 0xFF); out.write((b.size shr 16) and 0xFF)
        out.write((b.size shr 8) and 0xFF); out.write(b.size and 0xFF)
        out.write(b)
    }

    private fun pem(kind: String, der: ByteArray): String {
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)
        return "-----BEGIN $kind-----\n$b64\n-----END $kind-----\n"
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
}
