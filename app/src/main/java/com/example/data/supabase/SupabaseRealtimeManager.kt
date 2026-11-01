package com.example.data.supabase

import android.util.Log
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages a single multiplexed Supabase Realtime (Phoenix Channels v1) WebSocket connection
 * over OkHttp to receive instant `postgres_changes` push notifications for:
 * - `chat_messages`
 * - `chats`
 * - `interests`
 * - `notifications`
 * - `broadcasts`
 * - `users`
 *
 * Eliminates high-frequency HTTP polling loops when the WebSocket is connected.
 */
class SupabaseRealtimeManager(
    private val httpClientProvider: () -> OkHttpClient,
    private val supabaseUrlProvider: () -> String,
    private val supabaseAnonKeyProvider: () -> String,
    private val accessTokenProvider: () -> String?,
    private val isConfiguredProvider: () -> Boolean
) {
    companion object {
        private const val TAG = "SupabaseRealtime"
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val INITIAL_RECONNECT_DELAY_MS = 3_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val CHANNEL_TOPIC = "realtime:public:ghotok_changes"

        val WATCHED_TABLES = listOf(
            "chat_messages",
            "chats",
            "interests",
            "notifications",
            "broadcasts",
            "users"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refCounter = AtomicInteger(1)
    private val isConnecting = AtomicBoolean(false)

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var heartbeatJob: Job? = null

    @Volatile
    private var reconnectJob: Job? = null

    @Volatile
    private var reconnectAttempts = 0

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Emits the lowercase table name (e.g. `"chat_messages"`, `"chats"`, `"interests"`,
     * `"notifications"`, `"broadcasts"`, `"users"`) whenever a Postgres INSERT/UPDATE/DELETE
     * event arrives over the Realtime WebSocket.
     */
    private val _tableChangeEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val tableChangeEvents: SharedFlow<String> = _tableChangeEvents.asSharedFlow()

    fun ensureConnected() {
        if (!isConfiguredProvider()) return
        if (_isConnected.value || isConnecting.get()) return
        connectInternal()
    }

    fun notifyLocalTableMutation(table: String) {
        val clean = table.trim().lowercase(Locale.US)
        if (clean.isNotEmpty()) {
            _tableChangeEvents.tryEmit(clean)
        }
    }

    fun updateAccessToken(newToken: String?) {
        val ws = webSocket ?: return
        if (!_isConnected.value) return
        val tokenToUse = newToken?.takeIf { it.isNotBlank() } ?: supabaseAnonKeyProvider()
        val msg = JSONObject().apply {
            put("topic", CHANNEL_TOPIC)
            put("event", "access_token")
            put("payload", JSONObject().apply {
                put("access_token", tokenToUse)
            })
            put("ref", refCounter.getAndIncrement().toString())
        }
        runCatching { ws.send(msg.toString()) }
    }

    @Synchronized
    private fun connectInternal() {
        if (!isConfiguredProvider()) return
        if (_isConnected.value || !isConnecting.compareAndSet(false, true)) return

        val baseHttpUrl = supabaseUrlProvider().trim().trimEnd('/')
        val anonKey = supabaseAnonKeyProvider().trim()
        if (baseHttpUrl.isBlank() || anonKey.isBlank()) {
            isConnecting.set(false)
            return
        }

        val wsBaseUrl = when {
            baseHttpUrl.startsWith("https://", ignoreCase = true) ->
                "wss://" + baseHttpUrl.substringAfter("://")
            baseHttpUrl.startsWith("http://", ignoreCase = true) ->
                "ws://" + baseHttpUrl.substringAfter("://")
            else -> "wss://$baseHttpUrl"
        }
        val wsUrl = "$wsBaseUrl/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        runCatching {
            webSocket?.cancel()
            webSocket = httpClientProvider().newWebSocket(request, RealtimeSocketListener())
        }.onFailure { err ->
            Log.w(TAG, "Failed to initiate Supabase Realtime WebSocket: ${err.message}")
            isConnecting.set(false)
            scheduleReconnect()
        }
    }

    private fun sendJoinChannel(ws: WebSocket) {
        val postgresChanges = JSONArray()
        for (table in WATCHED_TABLES) {
            postgresChanges.put(
                JSONObject().apply {
                    put("event", "*")
                    put("schema", "public")
                    put("table", table)
                }
            )
        }

        val token = accessTokenProvider()?.takeIf { it.isNotBlank() } ?: supabaseAnonKeyProvider()
        val joinPayload = JSONObject().apply {
            put(
                "config",
                JSONObject().apply {
                    put("broadcast", JSONObject().apply { put("self", false) })
                    put("presence", JSONObject().apply { put("key", "") })
                    put("postgres_changes", postgresChanges)
                }
            )
            put("access_token", token)
        }

        val joinMsg = JSONObject().apply {
            put("topic", CHANNEL_TOPIC)
            put("event", "phx_join")
            put("payload", joinPayload)
            put("ref", refCounter.getAndIncrement().toString())
        }
        ws.send(joinMsg.toString())
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _isConnected.value) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!_isConnected.value) break
                val hb = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", refCounter.getAndIncrement().toString())
                }
                val sent = runCatching { ws.send(hb.toString()) }.getOrDefault(false)
                if (!sent) break
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val attempt = reconnectAttempts.coerceAtMost(4)
            val delayMs = (INITIAL_RECONNECT_DELAY_MS * (1L shl attempt)).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            reconnectAttempts++
            delay(delayMs)
            connectInternal()
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        _isConnected.value = false
        isConnecting.set(false)
        runCatching { webSocket?.close(1000, "Client disconnect") }
        webSocket = null
    }

    private inner class RealtimeSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnecting.set(false)
            _isConnected.value = true
            reconnectAttempts = 0
            runCatching {
                sendJoinChannel(webSocket)
                startHeartbeat(webSocket)
            }.onFailure { e ->
                Log.w(TAG, "Error joining realtime channel: ${e.message}")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                if (!text.startsWith("{")) return
                val root = JSONObject(text)
                val event = root.optString("event", "")
                val payload = root.optJSONObject("payload") ?: return

                if (event.equals("postgres_changes", ignoreCase = true) ||
                    event.equals("INSERT", ignoreCase = true) ||
                    event.equals("UPDATE", ignoreCase = true) ||
                    event.equals("DELETE", ignoreCase = true)
                ) {
                    val dataObj = payload.optJSONObject("data") ?: payload
                    val table = dataObj.optString("table", payload.optString("table", ""))
                        .trim()
                        .lowercase(Locale.US)
                    if (table.isNotEmpty()) {
                        _tableChangeEvents.tryEmit(table)
                        // Keep mirrored tables synchronized
                        if (table == "profiles") {
                            _tableChangeEvents.tryEmit("users")
                        }
                    }
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            _isConnected.value = false
            isConnecting.set(false)
            runCatching { webSocket.close(code, reason) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _isConnected.value = false
            isConnecting.set(false)
            heartbeatJob?.cancel()
            if (code != 1000) {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _isConnected.value = false
            isConnecting.set(false)
            heartbeatJob?.cancel()
            scheduleReconnect()
        }
    }
}
