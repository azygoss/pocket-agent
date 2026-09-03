// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import dev.pocketagent.transport.ChatBlock
import dev.pocketagent.transport.SessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// P14: Chat and terminal share the SAME SessionId. Blocks stream from gateway;
// full text never touches backend (only 256ch summaries do).
class ChatViewModel(val session: SessionId) {
    private val _blocks = MutableStateFlow<List<ChatBlock>>(emptyList())
    val blocks: StateFlow<List<ChatBlock>> = _blocks

    fun append(b: ChatBlock) {
        _blocks.value = _blocks.value + b
    }

    fun needsTerminal(b: ChatBlock): Boolean = b is ChatBlock.UnsupportedRedirectToTerminal
}
