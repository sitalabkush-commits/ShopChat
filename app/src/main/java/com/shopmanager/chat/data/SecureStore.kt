package com.shopmanager.chat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class ServerConfig(val host: String, val port: Int, val token: String)

class SecureStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "shop_secure_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Whatever is stored (may be incomplete); used to pre-fill the setup form. */
    fun draft() = ServerConfig(
        prefs.getString("host", "") ?: "",
        prefs.getInt("port", 8787),
        prefs.getString("token", "") ?: ""
    )

    /** Complete config or null if setup has not been done. */
    fun load(): ServerConfig? = draft().takeIf { it.host.isNotBlank() && it.token.isNotBlank() }

    fun save(c: ServerConfig) {
        prefs.edit().putString("host", c.host).putInt("port", c.port).putString("token", c.token).apply()
    }

    var voiceLang: String
        get() = prefs.getString("voice_lang", "ENGLISH") ?: "ENGLISH"
        set(v) { prefs.edit().putString("voice_lang", v).apply() }
}
