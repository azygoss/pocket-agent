// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport

// Hazır terminal profili: kayıtlı komut, oturum açılınca otomatik çalışır
// (örn. "codex" → agent doğrudan açılır). connectionId null ise açılışta
// host sorulur; host silinirse Room FK SET_NULL ile tekrar sor moduna düşer.
data class SavedProfile(
    val name: String,
    val command: String,
    val connectionId: String? = null,
    val id: String = "",
)
