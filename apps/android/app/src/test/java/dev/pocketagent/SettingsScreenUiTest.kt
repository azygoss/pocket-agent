// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.pocketagent.ui.SettingsScreen
import dev.pocketagent.ui.SettingsViewModel
import dev.pocketagent.ui.UsageViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Ayarlar smoke: dizin render edilir, detay sayfalar açılır ve geri
// dönülür; kopma-reconnect varsayılan açık (D023); kısayol düzenleyicisi
// "etiket=komut" biçimine yazar.
@RunWith(RobolectricTestRunner::class)
class SettingsScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private fun setContent(vm: SettingsViewModel = SettingsViewModel()) {
        val app = RuntimeEnvironment.getApplication() as dev.pocketagent.android.App
        val hk = dev.pocketagent.transport.TofuHostKeyStore(java.io.File.createTempFile("hostkeys", ".db"))
        rule.setContent { SettingsScreen(vm, UsageViewModel(), hk, app) }
        rule.waitForIdle()
    }

    @Test fun settingsIndexRenders() {
        setContent()
        rule.onNodeWithText("Görünüm").assertIsDisplayed()
        rule.onNodeWithText("Backend").assertIsDisplayed()
        rule.onNodeWithText("Hakkında").performScrollTo().assertIsDisplayed()
    }

    @Test fun detailPagesOpenAndReturn() {
        setContent()
        rule.onNodeWithText("Yedekleme ve kullanım").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Kullanım").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Geri").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Görünüm").assertIsDisplayed()
        rule.onNodeWithText("Hakkında").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Güncelleme").assertIsDisplayed()
    }

    @Test fun snippetEditorWritesLabelEqualsCommand() {
        val vm = SettingsViewModel()
        setContent(vm)
        rule.onNodeWithText("Terminal ve oturum").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Kısayol ekle").performScrollTo().performClick()
        rule.waitForIdle()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("gs")
        fields[1].performTextInput("git status")
        rule.onNodeWithText("Ekle").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(listOf("gs" to "git status"), vm.snippetList())
        rule.onAllNodesWithText("gs").fetchSemanticsNodes().isNotEmpty().let(org.junit.Assert::assertTrue)
    }

    @Test fun terminalTogglesWriteSettings() {
        val vm = SettingsViewModel()
        setContent(vm)
        rule.onNodeWithText("Terminal ve oturum").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Tuş titreşimi").performScrollTo().performClick()
        org.junit.Assert.assertFalse(vm.keyHaptics)
        rule.onNodeWithText("Terminalde ekranı açık tut").performScrollTo().performClick()
        org.junit.Assert.assertTrue(vm.keepScreenOn)
    }

    // Şemasız adres https:// ile kaydedilir, boş tenant "default" olur; uzak
    // http:// adres (telefonda çalışmaz) kaydedilmez, alanın altında söylenir.
    @Test fun backendAddressIsNormalizedAndCleartextRefused() {
        val vm = SettingsViewModel()
        setContent(vm)
        rule.onNodeWithText("Backend").performClick()
        rule.waitForIdle()
        val url = rule.onAllNodes(hasSetTextAction())[0]
        url.performTextInput("http://128.140.70.117:8080")
        rule.onNodeWithText("Kaydet").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Telefon şifresiz HTTP'ye bağlanmaz", substring = true).assertExists()
        org.junit.Assert.assertEquals("", vm.backendUrl)
        url.performTextClearance()
        url.performTextInput("agent.example.com/")
        rule.onNodeWithText("Kaydet").performScrollTo().performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals("https://agent.example.com", vm.backendUrl)
        org.junit.Assert.assertEquals("default", vm.tenantToken)
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
