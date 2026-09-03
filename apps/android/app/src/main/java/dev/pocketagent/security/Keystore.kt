// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.security

// P05: Keystore-backed key manager. Private keys never leave Keystore;
// biometric cancel => no read => no connect. Full impl with
// AndroidKeyStore + BiometricPrompt CryptoObject in P05 slice.
class KeystoreKeyManager {
    fun requiresBiometric(): Boolean = true
    fun aliasFor(profile: String): String = "pa-ssh-" + profile
    fun isAvailable(): Boolean = true // StrongBox/TEE probe at runtime
}
