// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pocketagent.ui.Route

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var current by remember { mutableStateOf(Route.Home) }
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Pocket Agent", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Route.entries.forEach { r ->
                        Button(
                            onClick = { current = r },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) { Text(r.label) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Active: ${current.label}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
