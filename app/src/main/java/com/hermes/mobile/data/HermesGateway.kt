package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HermesGateway {
    private val cookies = mutableMapOf<String, List<Cookie>>()
    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { this@HermesGateway.cookies[url.host] = cookies }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = this@HermesGateway.cookies[url.host].orEmpty().filter { it.matches(url) }
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var base = ""
    private var socket: WebSocket? = null
    private var nextId = 1
    private var sessionId: String? = null
    var onEvent: ((JSONObject) -> Unit)? = null
    var onConnection: ((Boolean, String?) -> Unit)? = null

    suspend fun login(baseUrl: String, username: String, password: String) = withContext(Dispatchers.IO) {
        base = baseUrl.trimEnd('/')
        val form = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("next", "/")
            .build()
        val req = Request.Builder().url("$base/auth/password-login").post(form).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful && r.code !in 300..399) error("Login failed: HTTP ${r.code}")
        }
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        val ticket = client.newCall(Request.Builder().url("$base/api/auth/ws-ticket").post(FormBody.Builder().build()).build())
            .execute().use { r ->
                if (!r.isSuccessful) error("WS ticket failed: HTTP ${r.code}")
                JSONObject(r.body?.string().orEmpty()).getString("ticket")
            }
        val wsUrl = base.replaceFirst("http", "ws") + "/api/ws?ticket=" + java.net.URLEncoder.encode(ticket, "UTF-8")
        socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) { onConnection?.invoke(true, null) }
            override fun onMessage(webSocket: WebSocket, text: String) { parse(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { parse(bytes.utf8()) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) { onConnection?.invoke(false, t.message) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { onConnection?.invoke(false, reason) }
        })
    }

    fun createSession() {
        request("session.create", emptyMap())
    }

    fun loadSessions() { request("session.list", emptyMap()) }

    fun history(id: String) { request("session.history", mapOf("session_id" to id)) }

    fun send(text: String) {
        val id = sessionId ?: run { createSession(); return }
        request("prompt.submit", mapOf("session_id" to id, "text" to text, "keep_transport" to true))
    }

    fun stop() { sessionId?.let { request("session.interrupt", mapOf("session_id" to it)) } }

    fun close() { socket?.close(1000, "client closed"); socket = null }

    private fun request(method: String, params: Map<String, Any?>) {
        val obj = JSONObject().put("jsonrpc", "2.0").put("id", nextId++).put("method", method)
        val p = JSONObject(); params.forEach { (k, v) -> p.put(k, v) }; obj.put("params", p)
        socket?.send(obj.toString() + "\n")
    }

    private fun parse(raw: String) {
        raw.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
            val result = o.optJSONObject("result")
            if (result != null && result.has("session_id")) sessionId = result.optString("session_id").takeIf { it.isNotBlank() }
            onEvent?.invoke(o)
        }
    }
}
