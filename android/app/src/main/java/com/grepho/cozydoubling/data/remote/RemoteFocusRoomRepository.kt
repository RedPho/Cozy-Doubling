package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.room.FocusRoomRepository
import com.grepho.cozydoubling.features.room.FocusSession
import com.grepho.cozydoubling.features.room.ParticipantPresence
import com.grepho.cozydoubling.features.room.RoomConnectionStatus
import com.grepho.cozydoubling.features.room.RoomSessionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class RemoteFocusRoomRepository(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository
) : FocusRoomRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _participants = MutableStateFlow<List<ParticipantPresence>>(emptyList())
    private val _channelStatus = MutableStateFlow<RoomConnectionStatus>(RoomConnectionStatus.DISCONNECTED)
    private val _broadcastFlow = MutableSharedFlow<ParticipantPresence>(replay = 1, extraBufferCapacity = 64)
    private val _sessionEvents = MutableSharedFlow<RoomSessionEvent>(extraBufferCapacity = 4)

    @Volatile private var activeWebSocket: WebSocket? = null
    @Volatile private var currentPresenceKey: String? = null
    @Volatile private var subscribedPresenceKey: String? = null
    @Volatile private var isRoomActive = false
    @Volatile private var connectionGeneration = 0L
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectAttempt = 0

    override fun joinRoom(presenceKey: String): Flow<List<ParticipantPresence>> {
        currentPresenceKey = presenceKey
        isRoomActive = true
        return _participants.asStateFlow()
    }

    override suspend fun subscribe() {
        if (!isRoomActive) return
        val pKey = currentPresenceKey ?: "default"
        if (_channelStatus.value == RoomConnectionStatus.CONNECTED &&
            activeWebSocket != null &&
            subscribedPresenceKey == pKey
        ) {
            println("DEBUG: RemoteFocusRoomRepository - Already connected for $pKey, skipping subscribe.")
            return
        }

        val token = apiClient.authToken ?: ""
        val profile = profileRepository.profile.value
        val name = profile?.displayName ?: "Cozy Doubler"
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")

        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        val myGeneration = ++connectionGeneration
        subscribedPresenceKey = pKey

        _channelStatus.value = RoomConnectionStatus.CONNECTING
        val oldWs = activeWebSocket
        activeWebSocket = null
        oldWs?.close(1000, "Reconnecting")

        val path = "/ws/room?token=$token&presence_key=$pKey&name=$encodedName"
        println("DEBUG: RemoteFocusRoomRepository - Opening room WebSocket at: $path")
        val ws = apiClient.openWebSocket(path, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (myGeneration != connectionGeneration || !isRoomActive) return
                activeWebSocket = webSocket
                reconnectAttempt = 0
                println("DEBUG: RemoteFocusRoomRepository - WebSocket CONNECTED successfully.")
                _channelStatus.value = RoomConnectionStatus.CONNECTED
                startHeartbeat(webSocket, myGeneration)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (myGeneration != connectionGeneration || !isRoomActive) return
                if (activeWebSocket != null && webSocket !== activeWebSocket) return
                println("DEBUG: RemoteFocusRoomRepository - onMessage received: $text")
                try {
                    val msg = apiClient.json.decodeFromString<WsRoomMessage>(text)
                    when (msg.type) {
                        "presence_state" -> {
                            val list = msg.participants?.map { it.toDomain() } ?: emptyList()
                            println("DEBUG: RemoteFocusRoomRepository - presence_state: ${list.size} other participants")
                            _participants.value = list
                        }
                        "presence_update" -> {
                            val p = msg.presence?.toDomain() ?: return
                            println("DEBUG: RemoteFocusRoomRepository - presence_update: ${p.name} (${p.id})")
                            _participants.update { current ->
                                current.filter { it.id != p.id } + p
                            }
                            _broadcastFlow.tryEmit(p)
                        }
                        "presence_leave" -> {
                            val pid = msg.presenceId ?: return
                            println("DEBUG: RemoteFocusRoomRepository - presence_leave: $pid")
                            _participants.update { current ->
                                current.filter { it.id != pid }
                            }
                        }
                        "session_expired" -> {
                            val expectedSessionId = pKey.substringAfter(":", "")
                            val expiredId = msg.sessionId ?: expectedSessionId
                            if (expiredId.isNotBlank() &&
                                expectedSessionId.isNotBlank() &&
                                expiredId != expectedSessionId
                            ) {
                                return
                            }
                            val reason = msg.reason ?: "abandoned_disconnect"
                            println("DEBUG: RemoteFocusRoomRepository - session_expired ($reason) for $expiredId")
                            isRoomActive = false
                            heartbeatJob?.cancel()
                            reconnectJob?.cancel()
                            _sessionEvents.tryEmit(RoomSessionEvent.SessionEndedByServer(expiredId, reason))
                        }
                    }
                } catch (e: Exception) {
                    println("DEBUG: RemoteFocusRoomRepository - JSON decode error on message: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (myGeneration != connectionGeneration) return
                println("DEBUG: RemoteFocusRoomRepository - WebSocket CLOSING ($code: $reason)")
                webSocket.close(code, reason)
                _channelStatus.value = RoomConnectionStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myGeneration != connectionGeneration) return
                println("DEBUG: RemoteFocusRoomRepository - WebSocket CLOSED ($code: $reason)")
                heartbeatJob?.cancel()
                if (activeWebSocket === webSocket) {
                    activeWebSocket = null
                }
                _channelStatus.value = RoomConnectionStatus.DISCONNECTED
                if (code != 1000 && code != 4001 && code != 4002) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myGeneration != connectionGeneration) return
                println("DEBUG: RemoteFocusRoomRepository - WebSocket FAILURE: ${t.message}")
                t.printStackTrace()
                heartbeatJob?.cancel()
                if (activeWebSocket === webSocket) {
                    activeWebSocket = null
                }
                _channelStatus.value = RoomConnectionStatus.DISCONNECTED
                scheduleReconnect()
            }
        })
        if (connectionGeneration == myGeneration && isRoomActive) {
            activeWebSocket = ws
        } else {
            ws.close(1000, "Stale socket")
        }
    }

    private fun startHeartbeat(webSocket: WebSocket, myGeneration: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive &&
                isRoomActive &&
                myGeneration == connectionGeneration &&
                activeWebSocket === webSocket
            ) {
                delay(60_000L)
                if (!isRoomActive || myGeneration != connectionGeneration || activeWebSocket !== webSocket) break
                try {
                    webSocket.send("""{"type":"ping"}""")
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isRoomActive) return
        reconnectJob?.cancel()
        val backoffMs = minOf(30_000L, 2_000L shl minOf(reconnectAttempt, 4))
        reconnectAttempt += 1
        reconnectJob = scope.launch {
            delay(backoffMs)
            if (isRoomActive && _channelStatus.value != RoomConnectionStatus.CONNECTED) {
                println("DEBUG: RemoteFocusRoomRepository - Attempting auto-reconnect (attempt=$reconnectAttempt)...")
                subscribe()
            }
        }
    }

    override fun getChannelStatus(): Flow<RoomConnectionStatus> = _channelStatus.asStateFlow()

    override suspend fun broadcastPresence(presence: ParticipantPresence) {
        _broadcastFlow.tryEmit(presence)
        try {
            val jsonStr = apiClient.json.encodeToString(WsPresenceUpdate(presence = ParticipantPresenceDto.fromDomain(presence)))
            activeWebSocket?.send(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun listenForBroadcasts(): Flow<ParticipantPresence> = _broadcastFlow.asSharedFlow()

    override suspend fun updatePresence(presence: ParticipantPresence) {
        try {
            val jsonStr = apiClient.json.encodeToString(WsPresenceUpdate(presence = ParticipantPresenceDto.fromDomain(presence)))
            activeWebSocket?.send(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun leaveRoom(presenceKey: String?) {
        if (presenceKey != null && currentPresenceKey != null && currentPresenceKey != presenceKey) {
            return
        }
        connectionGeneration += 1
        isRoomActive = false
        currentPresenceKey = null
        subscribedPresenceKey = null
        reconnectAttempt = 0
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "Leaving room")
        activeWebSocket = null
        _participants.value = emptyList()
        _channelStatus.value = RoomConnectionStatus.DISCONNECTED
    }

    override fun observeSessionEvents(): Flow<RoomSessionEvent> = _sessionEvents.asSharedFlow()

    override suspend fun startSession(): String? {
        val result = apiClient.post("/api/sessions/start", null) { body ->
            apiClient.json.decodeFromString<StartSessionResponseDto>(body)
        }
        val id = result.getOrNull()?.sessionId
        if (id == null) {
            println("DEBUG: RemoteFocusRoomRepository - startSession failed: ${result.exceptionOrNull()?.message}")
        } else {
            println("DEBUG: RemoteFocusRoomRepository - startSession success: $id")
        }
        return id
    }

    override suspend fun finishSession(sessionId: String, tasksCompleted: Int, taskText: String, endReason: String?) {
        val body = apiClient.json.encodeToString(
            FinishSessionRequestDto(
                tasksCompleted = tasksCompleted,
                lastTaskText = taskText,
                endReason = endReason
            )
        )
        apiClient.post("/api/sessions/$sessionId/finish", body) { }
        profileRepository.refreshProfile()
    }

    override suspend fun fetchSession(sessionId: String): FocusSession? {
        val result = apiClient.get("/api/sessions/$sessionId") { body ->
            apiClient.json.decodeFromString<FocusSessionDto>(body).toDomain()
        }
        return result.getOrNull()
    }
}
