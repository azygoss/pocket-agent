// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.parseAddHostLink
import dev.pocketagent.transport.parseDeepLink
import dev.pocketagent.ui.PocketAgentApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    // pocketagent://tmux|herdr deep-link'leri buradan UI'a akar.
    val deepLinkAction = MutableStateFlow<String?>(null)
    // pocketagent://add?host=…&user=… → Bağlantılar'da doldurulmuş diyalog.
    val addHostLink = MutableStateFlow<SavedConnection?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { PocketAgentApp(application as App, deepLinkAction, addHostLink) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val uri = i?.dataString ?: return
        parseAddHostLink(uri)?.let { addHostLink.value = it; return }
        val link = parseDeepLink(uri) ?: return
        deepLinkAction.value = link.provider.name
    }
}
