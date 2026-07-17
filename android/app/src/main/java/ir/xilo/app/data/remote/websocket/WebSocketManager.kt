package ir.xilo.app.data.remote.websocket

import ir.xilo.app.data.local.prefs.TokenManager
import ir.xilo.app.data.remote.AppEnvironment
import ir.xilo.app.data.remote.dto.MessageResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class WebSocketManager @Inject constructor(
    private val tokenManager: TokenManager,
    private val appEnvironment: AppEnvironment,
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val isConnecting = AtomicBoolean(false)
    private val shouldReconnect = AtomicBoolean(false)
    private var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS

    private val activeChatIds = ConcurrentHashMap.newKeySet<String>()
    private val activePostIds = ConcurrentHashMap.newKeySet<String>()
    private val activeUserIds = ConcurrentHashMap.newKeySet<String>()

    fun connect() {
        shouldReconnect.set(true)
        openSocketIfNeeded()
    }

    fun disconnect() {
        shouldReconnect.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
        synchronized(lock) {
            webSocket?.close(1000, "Normal closure")
            webSocket = null
        }
        isConnecting.set(false)
        _isConnected.value = false
    }

    fun joinChat(chatId: String) {
        if (chatId.isBlank()) return
        activeChatIds.add(chatId)
        sendEnvelope(
            event = RealtimeEvents.CHAT_JOIN,
            data = buildJsonObject { put("chat_id", chatId) },
        )
    }

    fun leaveChat(chatId: String) {
        if (chatId.isBlank()) return
        activeChatIds.remove(chatId)
        sendEnvelope(
            event = RealtimeEvents.CHAT_LEAVE,
            data = buildJsonObject { put("chat_id", chatId) },
        )
    }

    fun sendTyping(chatId: String, typing: Boolean = true) {
        if (chatId.isBlank()) return
        sendEnvelope(
            event = RealtimeEvents.USER_TYPING,
            data = buildJsonObject {
                put("chat_id", chatId)
                put("typing", typing)
            },
        )
    }

    /**
     * Optional WS send path. Prefer the REST/outbox path for durable delivery;
     * when used, [operationKey] MUST match the local outbox/optimistic key.
     */
    fun sendMessage(
        chatId: String,
        content: String,
        operationKey: String,
        type: String = "text",
        mediaUrl: String? = null,
        replyToId: String? = null,
    ) {
        if (chatId.isBlank() || operationKey.isBlank()) return
        sendEnvelope(
            event = RealtimeEvents.MESSAGE_SEND,
            operationKey = operationKey,
            data = buildJsonObject {
                put("chat_id", chatId)
                put("type", type)
                put("content", content)
                put("operation_key", operationKey)
                if (mediaUrl != null) put("media_url", mediaUrl)
                if (replyToId != null) put("reply_to_id", replyToId)
            },
        )
    }

    fun subscribeToPost(postId: String) {
        if (postId.isBlank()) return
        activePostIds.add(postId)
        // Legacy subscription shape (no version) remains supported by the hub.
        sendLegacy("""{"event":"subscribe:post","postId":"$postId"}""")
    }

    fun unsubscribeFromPost(postId: String) {
        if (postId.isBlank()) return
        activePostIds.remove(postId)
        sendLegacy("""{"event":"unsubscribe:post","postId":"$postId"}""")
    }

    fun subscribeToUser(userId: String) {
        if (userId.isBlank()) return
        activeUserIds.add(userId)
        sendLegacy("""{"event":"subscribe:user","channel":"$userId"}""")
    }

    fun unsubscribeFromUser(userId: String) {
        if (userId.isBlank()) return
        activeUserIds.remove(userId)
        sendLegacy("""{"event":"unsubscribe:user","channel":"$userId"}""")
    }

    fun activeJoinedChatIds(): Set<String> = activeChatIds.toSet()

    private fun openSocketIfNeeded() {
        synchronized(lock) {
            if (webSocket != null || isConnecting.get()) return
            val token = tokenManager.getAccessToken() ?: return
            if (!isConnecting.compareAndSet(false, true)) return

            val request = Request.Builder()
                .url(appEnvironment.websocketUrlWithToken(token))
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnecting.set(false)
                    reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
                    _isConnected.value = true
                    resubscribeAll()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    decodeAndEmit(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleDisconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    handleDisconnect()
                }
            })
        }
    }

    private fun handleDisconnect() {
        synchronized(lock) {
            webSocket = null
        }
        isConnecting.set(false)
        _isConnected.value = false
        if (shouldReconnect.get()) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val base = reconnectDelayMs
            val jitter = (base * JITTER_RATIO * Random.nextDouble()).toLong()
            delay(base + jitter)
            reconnectDelayMs = (base * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            if (shouldReconnect.get()) {
                openSocketIfNeeded()
            }
        }
    }

    private fun resubscribeAll() {
        activeChatIds.forEach { chatId ->
            sendEnvelope(
                event = RealtimeEvents.CHAT_JOIN,
                data = buildJsonObject { put("chat_id", chatId) },
            )
        }
        activePostIds.forEach { postId ->
            sendLegacy("""{"event":"subscribe:post","postId":"$postId"}""")
        }
        activeUserIds.forEach { userId ->
            sendLegacy("""{"event":"subscribe:user","channel":"$userId"}""")
        }
    }

    private fun sendEnvelope(
        event: String,
        data: JsonObject? = null,
        operationKey: String? = null,
        requestId: String = UUID.randomUUID().toString(),
    ) {
        val envelope = RealtimeEnvelope(
            version = REALTIME_PROTOCOL_VERSION,
            event = event,
            requestId = requestId,
            operationKey = operationKey,
            data = data,
        )
        val payload = json.encodeToString(RealtimeEnvelope.serializer(), envelope)
        synchronized(lock) {
            webSocket?.send(payload)
        }
    }

    private fun sendLegacy(payload: String) {
        synchronized(lock) {
            webSocket?.send(payload)
        }
    }

    private fun decodeAndEmit(text: String) {
        try {
            val envelope = json.decodeFromString(RealtimeEnvelope.serializer(), text)
            _events.tryEmit(toRealtimeEvent(envelope))
        } catch (_: Exception) {
            // Malformed frames must not tear down the socket or collector.
        }
    }

    private fun toRealtimeEvent(envelope: RealtimeEnvelope): RealtimeEvent {
        return try {
            when (envelope.event) {
                RealtimeEvents.MESSAGE_RECEIVE -> {
                    val data = envelope.data ?: return RealtimeEvent.Unknown(envelope)
                    RealtimeEvent.MessageReceive(
                        envelope = envelope,
                        message = json.decodeFromJsonElement(MessageResponse.serializer(), data),
                    )
                }
                RealtimeEvents.MESSAGE_EDIT -> {
                    val data = envelope.data ?: return RealtimeEvent.Unknown(envelope)
                    RealtimeEvent.MessageEdit(
                        envelope = envelope,
                        payload = json.decodeFromJsonElement(
                            RealtimeMessageEditPayload.serializer(),
                            data,
                        ),
                    )
                }
                RealtimeEvents.MESSAGE_DELETE -> {
                    val data = envelope.data ?: return RealtimeEvent.Unknown(envelope)
                    RealtimeEvent.MessageDelete(
                        envelope = envelope,
                        payload = json.decodeFromJsonElement(
                            RealtimeMessageDeletePayload.serializer(),
                            data,
                        ),
                    )
                }
                RealtimeEvents.MESSAGE_READ -> {
                    val data = envelope.data ?: return RealtimeEvent.Unknown(envelope)
                    RealtimeEvent.MessageRead(
                        envelope = envelope,
                        payload = json.decodeFromJsonElement(
                            RealtimeMessageReadPayload.serializer(),
                            data,
                        ),
                    )
                }
                RealtimeEvents.MESSAGE_REACTION -> {
                    val data = envelope.data ?: return RealtimeEvent.Unknown(envelope)
                    RealtimeEvent.MessageReaction(
                        envelope = envelope,
                        payload = json.decodeFromJsonElement(
                            RealtimeMessageReactionPayload.serializer(),
                            data,
                        ),
                    )
                }
                RealtimeEvents.USER_TYPING -> {
                    val data = envelope.data ?: return RealtimeEvent.Unknown(envelope)
                    RealtimeEvent.Typing(
                        envelope = envelope,
                        payload = json.decodeFromJsonElement(
                            RealtimeTypingPayload.serializer(),
                            data,
                        ),
                    )
                }
                RealtimeEvents.USER_ONLINE, RealtimeEvents.USER_OFFLINE -> {
                    val data = envelope.data ?: return RealtimeEvent.Unknown(envelope)
                    val payload = json.decodeFromJsonElement(
                        RealtimePresencePayload.serializer(),
                        data,
                    )
                    RealtimeEvent.Presence(
                        envelope = envelope,
                        payload = payload,
                        online = envelope.event == RealtimeEvents.USER_ONLINE || payload.online,
                    )
                }
                RealtimeEvents.ACK -> {
                    val data = envelope.data ?: return RealtimeEvent.Unknown(envelope)
                    RealtimeEvent.Ack(
                        envelope = envelope,
                        payload = json.decodeFromJsonElement(
                            RealtimeAckPayload.serializer(),
                            data,
                        ),
                    )
                }
                RealtimeEvents.ERROR -> {
                    val error = envelope.error ?: return RealtimeEvent.Unknown(envelope)
                    RealtimeEvent.Error(envelope = envelope, error = error)
                }
                else -> RealtimeEvent.Unknown(envelope)
            }
        } catch (_: Exception) {
            RealtimeEvent.Unknown(envelope)
        }
    }

    companion object {
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val JITTER_RATIO = 0.2
    }
}
