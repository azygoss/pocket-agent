// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import dev.pocketagent.ui.SettingsScreen
import dev.pocketagent.ui.SettingsViewModel
import dev.pocketagent.ui.UsageViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Ayarlar ekranı smoke: bölümler render edilir, kopma-reconnect switch'i
// varsayılan açık gelir (D023).
@RunWith(RobolectricTestRunner::class)
class SettingsScreenUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun settingsSectionsRender() {
        val app = RuntimeEnvironment.getApplication() as dev.pocketagent.android.App
        val hk = dev.pocketagent.transport.TofuHostKeyStore(
            java.io.File.createTempFile("hostkeys", ".db"),
        )
        rule.setContent {
            SettingsScreen(SettingsViewModel(), UsageViewModel(), hk, app)
        }
        rule.waitForIdle()
        rule.onNodeWithText("Görünüm").assertIsDisplayed()
        // dikey scroll'lu Column: ekran dışı bölümler kompoze edilir ama görünmez
        rule.onNodeWithText("Kullanım").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Hakkında").performScrollTo().assertIsDisplayed()
    }

    @Test fun autoReconnectOnDropDefaultsOn() {
        val vm = SettingsViewModel()
        assert(vm.autoReconnectOnDrop) { "varsayılan açık olmalı" }
        vm.toggleAutoReconnectOnDrop()
        assert(!vm.autoReconnectOnDrop)
        vm.toggleAutoReconnectOnDrop()
        assert(vm.autoReconnectOnDrop)
    }
}
