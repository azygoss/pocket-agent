// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

import java.security.MessageDigest
import java.util.Base64

// P04/P06: credentials NEVER land in Room/DataStore. Passwords and PEM keys
// live in this RAM-only sealed type and are wiped on disconnect/app death.
sealed interface Secret {
    data class Password(val value: String) : Secret
    data class PemKey(val pem: String, val passphrase: String? = null) : Secret
}

// TOFU host key material captured during handshake, shown to the user for
// pinning (SHA256 fingerprint, OpenSSH style).
data class PresentedKey(
    val host: String,
    val port: Int,
    val algorithm: String,
    val encoded: ByteArray,
) {
    val fingerprint: String
        get() = "SHA256:" + Base64.getEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(encoded))

    fun sameKeyAs(other: PresentedKey): Boolean =
        algorithm == other.algorithm && encoded.contentEquals(other.encoded)

    override fun equals(other: Any?): Boolean =
        other is PresentedKey && host == other.host && port == other.port && sameKeyAs(other)

    override fun hashCode(): Int = 31 * host.hashCode() + encoded.contentHashCode()
}

// Mapped failures — connector throws these, controller maps to TransportFailure.
class UnknownHostKeyException(val presented: PresentedKey) : Exception("unknown host key")
class HostKeyChangedException(val presented: PresentedKey, val pinned: PresentedKey) : Exception("host key changed")
class AuthFailedException(cause: Throwable? = null) : Exception("authentication failed", cause)
