// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import dev.pocketagent.transport.VoicePolicy
import org.junit.Assert.*
import org.junit.Test

class VoiceTest {
    @Test fun networkGatedByOptIn() {
        assertFalse(VoicePolicy.mayUseNetwork(false)) // default: on-device only
        assertTrue(VoicePolicy.mayUseNetwork(true))
    }
}
