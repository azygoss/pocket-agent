// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.session

// P09 sebat: process ölümü sonrası komut tekrarı YOK — yalnızca multiplexer
// re-attach. lastSession DataStore'da tutulur, açılışta resume edilir.
data class PersistedSession(
    val profileId: String,
    val provider: String, // SHELL|TMUX|ZELLIJ|HERDR
    val target: String,   // session/pane id
)

object SessionResume {
    fun resumeTarget(last: PersistedSession?, providerUp: Boolean): PersistedSession? {
        if (last == null || last.target.isBlank()) return null
        if (!providerUp) return null
        return last // re-attach, never re-run
    }
}
