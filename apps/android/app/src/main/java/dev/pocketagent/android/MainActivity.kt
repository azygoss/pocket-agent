// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.pocketagent.ui.PocketAgentApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketAgentApp(application as App) }
    }
}
