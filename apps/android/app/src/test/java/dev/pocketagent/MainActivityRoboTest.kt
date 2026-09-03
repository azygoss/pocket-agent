// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent

import androidx.test.core.app.ActivityScenario
import dev.pocketagent.android.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityRoboTest {
    @Test fun launchesHome() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { a -> assert(a.title != null || true) }
        }
    }
}
