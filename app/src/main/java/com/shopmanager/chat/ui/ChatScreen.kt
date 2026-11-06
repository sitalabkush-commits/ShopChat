package com.shopmanager.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shopmanager.chat.data.MessageEntity
import com.shopmanager.chat.util.localizeAmounts
import com.shopmanager.chat.voice.VoiceInput
import com.shopmanager.chat.voice.VoiceLang
import kotlinx.coroutines.launch

private val QUICK = listOf("Today's sales", "Low stock items", "Top customers owing money", "This month's profit")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    fun toast(msg: String) { scope.launch { snack.showSnackbar(msg) } }

    val voice = remember {
        VoiceInput(
            ctx,
            onText = { t, _ -> input = t },
            onListening = { listening = it },
            onError = { toast(it) }
        )
    }
    DisposableEffect(Unit) { onDispose { voice.destroy() } }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.start(vm.voiceLang.tag) else toast("Microphone permission is needed for voice input.")
    }

    fun toggleMic() {
        when {
            listening -> voice.stop()
            !voice.available -> toast("Speech recognition isn't available on this device.")
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                voice.start(vm.voiceLang.tag)
            else -> permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun send(q: String) {
        if (q.isBlank() || loading) return
        vm.send(q)
        input = ""
    }

    LaunchedEffect(messages.size, loading) {
        val count = messages.size + if (loading) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ShopManager Chat") },
                actions = {
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.Delete, "Clear chat") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty() && !loading) {
                    Text(
                        "Ask anything about your shop - sales, stock, customers, profit.\nTry a quick question below, or tap the mic.",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { MessageBubble(it) }
                    if (loading) item { TypingBubble() }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(QUICK) { q ->
                    AssistChip(onClick = { send(q) }, label = { Text(q) }, enabled = !loading)
                }
            }

            Row(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Voice:", style = MaterialTheme.typography.labelMedium)
                VoiceLang.values().forEach { l ->
                    FilterChip(
                        selected = vm.voiceLang == l,
                        onClick = { vm.selectVoiceLang(l) },
                        label = { Text(l.label) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text(if (listening) "Listening..." else "Ask about your shop") },
                    maxLines = 4, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { toggleMic() }) {
                    Icon(
                        Icons.Default.Mic, "Voice input",
                        tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !loading) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat?") },
            text = { Text("This deletes the saved conversation from this phone.") },
            confirmButton = { TextButton(onClick = { vm.clearChat(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MessageBubble(m: MessageEntity) {
    val isUser = m.role == "user"
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when {
        m.isError -> cs.errorContainer to cs.onErrorContainer
        isUser -> cs.primaryContainer to cs.onPrimaryContainer
        else -> cs.surfaceVariant to cs.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 340.dp)) {
            SelectionContainer {
                Box(Modifier.padding(12.dp)) {
                    if (isUser || m.isError) Text(m.content, style = MaterialTheme.typography.bodyLarge)
                    else MarkdownText(remember(m.content) { localizeAmounts(m.content) })
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("Thinking...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
