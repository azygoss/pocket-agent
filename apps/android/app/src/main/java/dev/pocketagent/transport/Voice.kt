// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.transport
// P16: on-device whisper default; BYOK opt-in required before any network.
// Deep links: pocketagent://tmux?... pocketagent://herdr?... (signed params only).
object VoicePolicy { fun mayUseNetwork(byokOptIn: Boolean): Boolean = byokOptIn }
