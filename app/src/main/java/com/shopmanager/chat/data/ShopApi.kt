package com.shopmanager.chat.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

enum class ErrorKind { NO_INTERNET, UNAUTHORIZED, SERVER_OFF, TIMEOUT, SERVER_ERROR, BLOCKED, CONFIG, UNKNOWN }

class ApiException(val kind: ErrorKind, message: String) : Exception(message)
class CleartextBlockedException : IOException("Cleartext HTTP blocked for non-private host")

data class HistoryItem(val role: String, val content: String)

/** Cleartext HTTP is allowed only for 192.168.x.x, 10.x.x.x and Tailscale 100.64.0.0/10. */
fun isAllowedCleartextHost(host: String): Boolean {
    val m = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(host) ?: return false
    val o = m.groupValues.drop(1).map { it.toInt() }
    if (o.any { it > 255 }) return false
    return o[0] == 10 || (o[0] == 192 && o[1] == 168) || (o[0] == 100 && o[1] in 64..127)
}

class ShopApi(private val context: Context) {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            if (req.url.scheme == "http" && !isAllowedCleartextHost(req.url.host)) {
                throw CleartextBlockedException()
            }
            chain.proceed(req)
        }
        .build()

    /** Returns the server's answer text. Throws [ApiException]. */
    suspend fun chat(cfg: ServerConfig, question: String, history: List<HistoryItem>): String {
        val arr = JSONArray()
        history.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().put("question", question).put("history", arr).toString()
        return extractText(call(cfg, "chat", body))
    }

    /** Returns a short status string on success. Throws [ApiException]. */
    suspend fun health(cfg: ServerConfig): String {
        val raw = call(cfg, "health", null).trim()
        if (raw.startsWith("{")) {
            try {
                val o = JSONObject(raw)
                if (o.has("status")) return o.get("status").toString()
            } catch (ignored: JSONException) { }
        }
        return raw.take(80).ifBlank { "OK" }
    }

    private fun extractText(body: String): String {
        val t = body.trim()
        if (t.startsWith("{")) {
            try {
                val o = JSONObject(t)
                for (k in listOf("answer", "response", "reply", "text", "message", "result")) {
                    if (o.has(k) && !o.isNull(k)) return o.get(k).toString()
                }
            } catch (ignored: JSONException) { }
        }
        return t
    }

    private fun hasNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun buildUrl(cfg: ServerConfig, path: String): HttpUrl {
        var h = cfg.host.trim()
        var scheme = "http"
        if (h.startsWith("https://", true)) { scheme = "https"; h = h.substring(8) }
        else if (h.startsWith("http://", true)) h = h.substring(7)
        h = h.substringBefore('/')
        return HttpUrl.Builder().scheme(scheme).host(h).port(cfg.port).addPathSegment(path).build()
    }

    private suspend fun call(cfg: ServerConfig, path: String, jsonBody: String?): String =
        withContext(Dispatchers.IO) {
            if (!hasNetwork()) {
                throw ApiException(ErrorKind.NO_INTERNET, "No network connection. Turn on Wi-Fi or mobile data (or your Tailscale VPN) and try again.")
            }
            val url = try {
                buildUrl(cfg, path)
            } catch (e: IllegalArgumentException) {
                throw ApiException(ErrorKind.CONFIG, "The server address looks invalid. Open Settings and enter just the IP or host name (no port).")
            }
            for (attempt in 1..3) {
                try {
                    return@withContext once(url, cfg, jsonBody)
                } catch (e: ApiException) {
                    if (e.kind != ErrorKind.SERVER_ERROR || attempt == 3) throw e
                } catch (e: CleartextBlockedException) {
                    throw ApiException(ErrorKind.BLOCKED, "Plain HTTP is only allowed for private addresses (192.168.x.x, 10.x.x.x) and Tailscale (100.x.x.x). Use one of those IPs.")
                } catch (e: SocketTimeoutException) {
                    if (attempt >= 2) throw ApiException(ErrorKind.TIMEOUT, "The server did not answer within 60 seconds. It may be busy - please try again.")
                } catch (e: UnknownHostException) {
                    throw ApiException(ErrorKind.SERVER_OFF, "Server address not found. Check the IP or host name in Settings.")
                } catch (e: SocketException) {
                    if (attempt == 3) throw ApiException(ErrorKind.SERVER_OFF, "Can't reach the server. Make sure the shop app is running on the PC, and that this phone is on the same Wi-Fi (or Tailscale).")
                } catch (e: IOException) {
                    if (attempt == 3) throw ApiException(ErrorKind.UNKNOWN, "Network error: ${e.message ?: "unknown"}")
                } catch (e: IllegalArgumentException) {
                    throw ApiException(ErrorKind.CONFIG, "The token contains invalid characters. Re-enter it in Settings.")
                }
                delay(attempt * 1000L)
            }
            throw ApiException(ErrorKind.UNKNOWN, "Request failed.")
        }

    private fun once(url: HttpUrl, cfg: ServerConfig, jsonBody: String?): String {
        val rb = Request.Builder().url(url)
            .header("Authorization", "Bearer ${cfg.token}")
            .header("Accept", "application/json")
        if (jsonBody != null) rb.post(jsonBody.toRequestBody(json)) else rb.get()
        client.newCall(rb.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            return when {
                resp.isSuccessful -> body
                resp.code == 401 || resp.code == 403 ->
                    throw ApiException(ErrorKind.UNAUTHORIZED, "Wrong token (401). Open Settings and re-enter the token from the shop app.")
                resp.code >= 500 ->
                    throw ApiException(ErrorKind.SERVER_ERROR, "The shop server had an internal error (HTTP ${resp.code}).")
                else ->
                    throw ApiException(ErrorKind.UNKNOWN, "Unexpected reply from server (HTTP ${resp.code}). Check the port and app version.")
            }
        }
    }
}
