package com.shopmanager.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.chat.ui.ChatScreen
import com.shopmanager.chat.ui.ChatViewModel
import com.shopmanager.chat.ui.SetupScreen
import com.shopmanager.chat.ui.ShopManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShopManagerTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: ChatViewModel = viewModel()
                    if (vm.showSetup) {
                        SetupScreen(
                            initial = vm.draft(),
                            canGoBack = vm.config != null,
                            onTest = { vm.test(it) },
                            onSave = { vm.saveConfig(it) },
                            onBack = { vm.showSetup = false }
                        )
                    } else {
                        ChatScreen(vm) { vm.showSetup = true }
                    }
                }
            }
        }
    }
}
