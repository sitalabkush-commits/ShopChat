package com.shopmanager.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.shopmanager.chat.data.ServerConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initial: ServerConfig,
    canGoBack: Boolean,
    onTest: suspend (ServerConfig) -> Pair<Boolean, String>,
    onSave: (ServerConfig) -> Unit,
    onBack: () -> Unit
) {
    // Deliberately `remember` (not rememberSaveable) so the token is never written to saved-state bundles.
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var token by remember { mutableStateOf(initial.token) }
    var showToken by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = canGoBack, onBack = onBack)

    fun build(): ServerConfig? {
        val p = port.trim().toIntOrNull()
        when {
            host.isBlank() -> error = "Enter the server address."
            p == null || p !in 1..65535 -> error = "Port must be between 1 and 65535."
            token.isBlank() -> error = "Enter the access token."
            else -> { error = null; return ServerConfig(host.trim(), p, token.trim()) }
        }
        return null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server setup") },
                navigationIcon = {
                    if (canGoBack) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Enter the address of the PC running your shop-management app. Use its local IP (192.168.x.x or 10.x.x.x) or its Tailscale IP (100.x.x.x), and the access token from the app.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = host, onValueChange = { host = it }, singleLine = true,
                label = { Text("Server address (IP or host)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit) }, singleLine = true,
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it }, singleLine = true,
                label = { Text("Access token") },
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide" else "Show") } },
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let { (ok, msg) ->
                Text(msg, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        val cfg = build() ?: return@OutlinedButton
                        testing = true; result = null
                        scope.launch { result = onTest(cfg); testing = false }
                    }
                ) {
                    if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Test connection")
                }
                Button(onClick = { build()?.let(onSave) }) { Text("Save") }
            }
        }
    }
}
