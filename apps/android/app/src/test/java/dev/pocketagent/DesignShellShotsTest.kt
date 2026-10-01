// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.pocketagent.android.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// Gerçek kabuk görüntüleri (MainActivity): chrome üst şerit + sheet + gezinme.
// Compose test kuralı kareleri sürer — ayar yükleme kapısı açıldıktan sonraki
// kompozisyon görünür. PA_SHOTS_DIR yoksa atlanır.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class DesignShellShotsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val out = System.getenv("PA_SHOTS_DIR")?.let(::File)

    @Before fun gate() = assumeTrue(out != null)

    private fun shot(name: String) {
        repeat(10) {
            rule.mainClock.advanceTimeBy(200)
            rule.waitForIdle()
            Thread.sleep(80)
        }
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun appShell() = shot("app-shell")

    // Geniş pencere: alt bar yerine sol ray + 760dp okunabilir ölçü.
    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun appShellWide() = shot("app-shell-wide")
}
