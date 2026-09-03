// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.pocketagent.data.ConnectionRepository
import dev.pocketagent.transport.ConnectionState
import dev.pocketagent.transport.SavedConnection
import dev.pocketagent.transport.Secret
import dev.pocketagent.transport.TerminalController
import dev.pocketagent.transport.TerminalTransport
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(
    repo: ConnectionRepository,
    terminal: TerminalController,
    onConnected: () -> Unit,
) {
    val items by repo.items.collectAsState()
    val active by terminal.connectedTo.collectAsState()
    val state by terminal.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Host ekle") },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (items.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Kayıtlı host yok", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "SSH erişimi olan bir host ekle. Parola ve anahtarlar yalnızca RAM'de tutulur, veritabanına yazılmaz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                ) {
                    items(items, key = { it.id }) { c ->
                        ConnectionCard(
                            conn = c,
                            isActive = active?.id == c.id && state == ConnectionState.ACTIVE,
                            onConnect = {
                                scope.launch {
                                    terminal.connect(c, repo.secret(c.id))
                                    onConnected()
                                }
                            },
                            onDelete = { scope.launch { repo.delete(c.id) } },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddConnectionDialog(
            onDismiss = { showAdd = false },
            onSave = { conn, secret ->
                scope.launch {
                    repo.upsert(conn, secret)
                    showAdd = false
                }
            },
        )
    }
}

@Composable
private fun ConnectionCard(
    conn: SavedConnection,
    isActive: Boolean,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(conn.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${conn.user}@${conn.host}:${conn.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isActive) StateDot(ConnectionState.ACTIVE)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                conn.transportOrder.forEach { t ->
                    FilterChip(selected = t == TerminalTransport.SSH, onClick = {}, label = { Text(t.name) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onConnect) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (isActive) "Aç" else "Bağlan")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AddConnectionDialog(
    onDismiss: () -> Unit,
    onSave: (SavedConnection, Secret?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
    var authKind by remember { mutableStateOf("password") }
    var secretText by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni host") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Ad") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Host (örn. 192.168.1.10)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) },
                        label = { Text("Port") }, singleLine = true, modifier = Modifier.width(96.dp),
                    )
                    OutlinedTextField(
                        user, { user = it },
                        label = { Text("Kullanıcı") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = authKind == "password",
                        onClick = { authKind = "password"; secretText = "" },
                        label = { Text("Parola") },
                    )
                    FilterChip(
                        selected = authKind == "pem",
                        onClick = { authKind = "pem"; secretText = "" },
                        label = { Text("PEM anahtarı") },
                    )
                }
                if (authKind == "password") {
                    OutlinedTextField(
                        secretText, { secretText = it },
                        label = { Text("Parola (yalnız RAM)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                } else {
                    OutlinedTextField(
                        secretText, { secretText = it },
                        label = { Text("PEM içeriği (yalnız RAM)") },
                        minLines = 3, maxLines = 5,
                    )
                }
                if (errors.isNotEmpty()) {
                    Text(
                        "Eksik/hatalı: ${errors.joinToString()}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "İlk bağlantıda host parmak izi gösterilir ve pinlenir (TOFU).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cred = if (authKind == "password") "ram:password" else "ram:pem"
                val conn = SavedConnection(
                    name = name.trim(),
                    host = host.trim(),
                    port = port.toIntOrNull() ?: 0,
                    user = user.trim(),
                    credentialRef = cred,
                    transportOrder = listOf(TerminalTransport.SSH),
                )
                val errs = conn.validate().toMutableList()
                if (secretText.isBlank()) errs += "secret"
                if (errs.isEmpty()) {
                    val secret = if (authKind == "password") Secret.Password(secretText)
                    else Secret.PemKey(secretText)
                    onSave(conn, secret)
                } else {
                    errors = errs
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
