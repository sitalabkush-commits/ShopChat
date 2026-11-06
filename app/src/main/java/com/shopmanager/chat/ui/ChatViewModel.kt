package com.shopmanager.chat.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.shopmanager.chat.data.ApiException
import com.shopmanager.chat.data.AppDb
import com.shopmanager.chat.data.HistoryItem
import com.shopmanager.chat.data.MessageEntity
import com.shopmanager.chat.data.SecureStore
import com.shopmanager.chat.data.ServerConfig
import com.shopmanager.chat.data.ShopApi
import com.shopmanager.chat.voice.VoiceLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private val dao = Room.databaseBuilder(app, AppDb::class.java, "chat.db").build().dao()
    private val api = ShopApi(app)

    val messages: StateFlow<List<MessageEntity>> =
        dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    var config by mutableStateOf(store.load())
        private set
    var showSetup by mutableStateOf(config == null)
    var voiceLang by mutableStateOf(VoiceLang.from(store.voiceLang))
        private set

    fun draft() = store.draft()

    fun saveConfig(c: ServerConfig) {
        store.save(c)
        config = c
        showSetup = false
    }

    fun setVoiceLang(l: VoiceLang) {
        voiceLang = l
        store.voiceLang = l.name
    }

    suspend fun test(c: ServerConfig): Pair<Boolean, String> = try {
        true to "Connected - server status: ${api.health(c)}"
    } catch (e: ApiException) {
        false to (e.message ?: "Connection failed.")
    }

    fun clearChat() { viewModelScope.launch { dao.clear() } }

    fun send(question: String) {
        val cfg = config ?: run { showSetup = true; return }
        val q = question.trim()
        if (q.isEmpty() || _loading.value) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val history = dao.last(10).reversed().map { HistoryItem(it.role, it.content) }
                dao.insert(MessageEntity(role = "user", content = q))
                val answer = api.chat(cfg, q, history)
                dao.insert(MessageEntity(role = "assistant", content = answer))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                dao.insert(MessageEntity(role = "assistant", content = e.message ?: "Request failed.", isError = true))
            } catch (e: Exception) {
                dao.insert(MessageEntity(role = "assistant", content = "Unexpected error: ${e.message}", isError = true))
            } finally {
                _loading.value = false
            }
        }
    }
}
