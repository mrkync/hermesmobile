package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class HermesGateway {

    private val cookies =
        mutableMapOf<String, MutableList<Cookie>>()

    private val client =
        OkHttpClient.Builder()
            .cookieJar(
                object : CookieJar {

                    override fun saveFromResponse(
                        url: HttpUrl,
                        cookiesFromResponse: List<Cookie>
                    ) {

                        val current =
                            cookies[url.host].orEmpty()

                        val merged =
                            (
                                current.filter { old ->
                                    cookiesFromResponse.none {
                                        it.name == old.name &&
                                        it.path == old.path
                                    }
                                } +
                                    cookiesFromResponse
                            ).toMutableList()

                        cookies[url.host] = merged
                    }

                    override fun loadForRequest(
                        url: HttpUrl
                    ): List<Cookie> {

                        return cookies[url.host]
                            .orEmpty()
                            .filter {
                                it.matches(url)
                            }
                    }
                }
            )
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )
            .build()

    private var base = ""

    private var socket: WebSocket? = null

    private var nextId = 1

    private var sessionId: String? = null

    var onEvent:
        ((JSONObject) -> Unit)? = null

    var onConnection:
        ((Boolean, String?) -> Unit)? = null


    suspend fun login(
        url: String,
        user: String,
        pass: String
    ) = withContext(Dispatchers.IO) {

        base =
            url.trimEnd('/')

        val json =
            JSONObject()
                .put(
                    "provider",
                    "basic"
                )
                .put(
                    "username",
                    user.trim()
                )
                .put(
                    "password",
                    pass
                )
                .toString()

        val body =
            json.toRequestBody(
                "application/json; charset=utf-8"
                    .toMediaType()
            )

        val request =
            Request.Builder()
                .url(
                    "$base/auth/password-login"
                )
                .post(body)
                .build()

        client.newCall(request)
            .execute()
            .use { response ->

                if (!response.isSuccessful) {

                    val responseBody =
                        response.body
                            ?.string()
                            .orEmpty()
                            .take(300)

                    if (
                        responseBody.isBlank()
                    ) {

                        error(
                            "Login failed: HTTP ${response.code}"
                        )

                    } else {

                        error(
                            "Login failed: HTTP ${response.code} - $responseBody"
                        )
                    }
                }
            }
    }


    suspend fun connect() =
        withContext(Dispatchers.IO) {

            val ticketRequest =
                Request.Builder()
                    .url(
                        "$base/api/auth/ws-ticket"
                    )
                    .post(
                        ByteArray(0)
                            .toRequestBody(null)
                    )
                    .build()

            val ticket =
                client.newCall(
                    ticketRequest
                )
                    .execute()
                    .use { response ->

                        if (
                            !response.isSuccessful
                        ) {

                            error(
                                "WS ticket failed: HTTP ${response.code}"
                            )
                        }

                        JSONObject(
                            response.body
                                ?.string()
                                .orEmpty()
                        )
                            .getString(
                                "ticket"
                            )
                    }


            val wsBase =
                when {

                    base.startsWith(
                        "https://"
                    ) ->
                        base.replaceFirst(
                            "https://",
                            "wss://"
                        )

                    base.startsWith(
                        "http://"
                    ) ->
                        base.replaceFirst(
                            "http://",
                            "ws://"
                        )

                    else ->
                        base
                }


            socket =
                client.newWebSocket(

                    Request.Builder()
                        .url(

                            wsBase +
                                "/api/ws?ticket=" +
                                URLEncoder.encode(
                                    ticket,
                                    "UTF-8"
                                )
                        )
                        .build(),

                    object :
                        WebSocketListener() {


                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response
                        ) {

                            onConnection?.invoke(
                                true,
                                null
                            )
                        }


                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String
                        ) {

                            parse(
                                text
                            )
                        }


                        override fun onMessage(
                            webSocket: WebSocket,
                            bytes: ByteString
                        ) {

                            parse(
                                bytes.utf8()
                            )
                        }


                        override fun onFailure(
                            webSocket: WebSocket,
                            throwable: Throwable,
                            response: Response?
                        ) {

                            onConnection?.invoke(
                                false,
                                throwable.message
                            )
                        }


                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String
                        ) {

                            onConnection?.invoke(
                                false,
                                reason
                            )
                        }
                    }
                )
        }


    fun send(
        text: String
    ) {

        val id =
            sessionId
                ?: run {

                    request(
                        "session.create",
                        emptyMap()
                    )

                    return
                }

        request(

            "prompt.submit",

            mapOf(

                "session_id" to id,

                "text" to text,

                "keep_transport" to true
            )
        )
    }


    fun stop() {

        sessionId?.let {

            request(

                "session.interrupt",

                mapOf(
                    "session_id" to it
                )
            )
        }
    }


    fun close() {

        socket?.close(
            1000,
            "client closed"
        )

        socket = null
    }


    private fun request(

        method: String,

        params: Map<String, Any?>
    ) {

        val request =
            JSONObject()
                .put(
                    "jsonrpc",
                    "2.0"
                )
                .put(
                    "id",
                    nextId++
                )
                .put(
                    "method",
                    method
                )

        val parameters =
            JSONObject()

        params.forEach {

            (
                key,
                value
            ) ->

            parameters.put(
                key,
                value
            )
        }

        request.put(
            "params",
            parameters
        )

        socket?.send(
            request.toString() +
                "\n"
        )
    }


    private fun parse(
        raw: String
    ) {

        raw.lineSequence()
            .filter {

                it.isNotBlank()
            }
            .forEach { line ->

                val json =
                    runCatching {

                        JSONObject(
                            line
                        )
                    }
                        .getOrNull()
                        ?: return@forEach


                json.optJSONObject(
                    "result"
                )
                    ?.let {

                        if (
                            it.has(
                                "session_id"
                            )
                        ) {

                            sessionId =
                                it.optString(
                                    "session_id"
                                )
                                    .takeIf {
                                        value ->
                                        value.isNotBlank()
                                    }
                        }
                    }


                onEvent?.invoke(
                    json
                )
            }
    }
}
