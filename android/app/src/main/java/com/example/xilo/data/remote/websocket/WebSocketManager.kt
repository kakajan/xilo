package com.example.xilo.data.remote.websocket

import com.example.xilo.data.local.prefs.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val tokenManager: TokenManager
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Disable timeout for websocket
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _isConnected = MutableSharedFlow<Boolean>(replay = 1)
    val isConnected: SharedFlow<Boolean> = _isConnected.asSharedFlow()

    private var isConnecting = false
    private var shouldReconnect = true
    private var reconnectDelay = 1000L
    private val maxReconnectDelay = 30000L

    private val activeSubscriptions = mutableSetOf<String>()

    fun connect() {
        if (webSocket != null || isConnecting) return
        val token = tokenManager.getAccessToken() ?: return
        isConnecting = true
        shouldReconnect = true

        val request = Request.Builder()
            .url("ws://10.0.2.2:8888/ws?token=$token")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                reconnectDelay = 1000L
                _isConnected.tryEmit(true)
                // Restore active subscriptions on reconnect
                activeSubscriptions.forEach { channel ->
                    if (channel.startsWith("post:")) {
                        subscribeToPost(channel.removePrefix("post:"))
                    } else if (channel.startsWith("user:")) {
                        subscribeToUser(channel.removePrefix("user:"))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _messages.tryEmit(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.tryEmit(false)
                this@WebSocketManager.webSocket = null
                if (shouldReconnect) {
                    reconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.tryEmit(false)
                this@WebSocketManager.webSocket = null
                isConnecting = false
                if (shouldReconnect) {
                    reconnect()
                }
            }
        })
    }

    private fun reconnect() {
        scope.launch {
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(maxReconnectDelay)
            connect()
        }
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnecting = false
        _isConnected.tryEmit(false)
    }

    fun subscribeToPost(postId: String) {
        val channel = "post:$postId"
        activeSubscriptions.add(channel)
        sendEvent("subscribe:post", mapOf("postId" to postId))
    }

    fun unsubscribeFromPost(postId: String) {
        val channel = "post:$postId"
        activeSubscriptions.remove(channel)
        sendEvent("unsubscribe:post", mapOf("postId" to postId))
    }

    fun subscribeToUser(userId: String) {
        val channel = "user:$userId"
        activeSubscriptions.add(channel)
        sendEvent("subscribe:user", mapOf("channel" to userId))
    }

    private fun sendEvent(event: String, data: Map<String, String>) {
        val payload = buildString {
            append("{\"event\":\"$event\"")
            data.forEach { (key, value) ->
                append(",\"$key\":\"$value\"")
            }
            append("}")
        }
        webSocket?.send(payload)
    }

    fun sendChatMessage(chatId: String, content: String) {
        sendEvent("message.send", mapOf("chatId" to chatId, "content" to content))
    }
}
