// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport
// P14: Chat View models share SessionId with terminal; gateway-only streaming.
sealed interface ChatBlock {
  data class Message(val text: String) : ChatBlock
  data class Thinking(val text: String) : ChatBlock
  data class ToolCard(val tool: String, val state: String) : ChatBlock
  data class MiniDiff(val path: String) : ChatBlock // bytes via gateway, never backend
  data object UnsupportedRedirectToTerminal : ChatBlock
}
