package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.room.FocusRoomRepository
import com.grepho.cozydoubling.features.room.FocusSession
import com.grepho.cozydoubling.features.room.ParticipantPresence
import com.grepho.cozydoubling.features.room.RoomConnectionStatus
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

    private val _participants = MutableStateFlow<List<ParticipantPresence>>(emptyList())
    private val _channelStatus = MutableStateFlow<RoomConnectionStatus>(RoomConnectionStatus.DISCONNECTED)
    private val _broadcastFlow = MutableSharedFlow<ParticipantPresence>(replay = 0, extraBufferCapacity = 64)

    private var activeWebSocket: WebSocket? = null
    private var currentPresenceKey: String? = null

    override fun joinRoom(presenceKey: String): Flow<List<ParticipantPresence>> {
        currentPresenceKey = presenceKey
        return _participants.asStateFlow()
    }

    override suspend fun subscribe() {
        val token = apiClient.authToken ?: ""
        val pKey = currentPresenceKey ?: "default"
        val profile = profileRepository.profile.value
        val name = profile?.displayName ?: "Cozy Doubler"

        _channelStatus.value = RoomConnectionStatus.CONNECTING
        activeWebSocket?.close(1000, "Reconnecting")

        val path = "/ws/room?token=$token&presence_key=$pKey&name=$name"
        activeWebSocket = apiClient.openWebSocket(path, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _channelStatus.value = RoomConnectionStatus.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = apiClient.json.decodeFromString<WsRoomMessage>(text)
                    when (msg.type) {
                        "presence_state" -> {
                            _participants.value = msg.participants ?: emptyList()
                        }
                        "presence_update" -> {
                            val p = msg.presence ?: return
                            _participants.update { current ->
                                current.filter { it.id != p.id } + p
                            }
                            _broadcastFlow.tryEmit(p)
                        }
                        "presence_leave" -> {
                            val pid = msg.presenceId ?: return
                            _participants.update { current ->
                                current.filter { it.id != pid }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _channelStatus.value = RoomConnectionStatus.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _channelStatus.value = RoomConnectionStatus.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _channelStatus.value = RoomConnectionStatus.DISCONNECTED
            }
        })
    }

    override fun getChannelStatus(): Flow<RoomConnectionStatus> = _channelStatus.asStateFlow()

    override suspend fun broadcastPresence(presence: ParticipantPresence) {
        _broadcastFlow.tryEmit(presence)
        try {
            val jsonStr = apiClient.json.encodeToString(WsPresenceUpdate(presence = presence))
            activeWebSocket?.send(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun listenForBroadcasts(): Flow<ParticipantPresence> = _broadcastFlow.asSharedFlow()

    override suspend fun updatePresence(presence: ParticipantPresence) {
        try {
            val jsonStr = apiClient.json.encodeToString(WsPresenceUpdate(presence = presence))
            activeWebSocket?.send(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun leaveRoom() {
        activeWebSocket?.close(1000, "Leaving room")
        activeWebSocket = null
        _participants.value = emptyList()
        _channelStatus.value = RoomConnectionStatus.DISCONNECTED
    }

    override suspend fun startSession(): String? {
        val result = apiClient.post("/api/sessions/start", null) { body ->
            apiClient.json.decodeFromString<StartSessionResponseDto>(body)
        }
        return result.getOrNull()?.sessionId
    }

    override suspend fun finishSession(sessionId: String, tasksCompleted: Int, taskText: String) {
        val body = apiClient.json.encodeToString(FinishSessionRequestDto(tasksCompleted = tasksCompleted, lastTaskText = taskText))
        apiClient.post("/api/sessions/$sessionId/finish", body) { }
        profileRepository.refreshProfile()
    }

    override suspend fun fetchSession(sessionId: String): FocusSession? {
        val result = apiClient.get("/api/sessions/$sessionId") { body ->
            apiClient.json.decodeFromString<FocusSession>(body)
        }
        return result.getOrNull()
    }
}
