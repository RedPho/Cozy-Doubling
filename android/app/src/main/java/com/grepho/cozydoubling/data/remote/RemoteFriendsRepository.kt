package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.friends.FriendUiState
import com.grepho.cozydoubling.features.friends.FriendsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class RemoteFriendsRepository(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository? = null
) : FriendsRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _friends = MutableStateFlow<List<FriendUiState>>(emptyList())
    override val friends: StateFlow<List<FriendUiState>> = _friends.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<Profile>>(emptyList())
    override val pendingRequests: StateFlow<List<Profile>> = _pendingRequests.asStateFlow()

    private var friendsWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var isAuthenticated = false

    init {
        if (authRepository != null) {
            scope.launch {
                authRepository.authStatus.collect { status ->
                    when (status) {
                        is AuthStatus.Authenticated -> {
                            isAuthenticated = true
                            fetchFriendsWithStories()
                            fetchIncomingRequests()
                            connectFriendsWebSocket()
                        }
                        else -> {
                            isAuthenticated = false
                            reconnectAttempt = 0
                            reconnectJob?.cancel()
                            friendsWebSocket?.close(1000, "Signed out")
                            friendsWebSocket = null
                            _friends.value = emptyList()
                            _pendingRequests.value = emptyList()
                        }
                    }
                }
            }
        } else {
            scope.launch {
                profileRepository.profile.collect { profile ->
                    if (profile != null) {
                        fetchFriendsWithStories()
                        fetchIncomingRequests()
                    } else {
                        _friends.value = emptyList()
                        _pendingRequests.value = emptyList()
                    }
                }
            }
        }
    }

    private fun connectFriendsWebSocket() {
        val token = apiClient.authToken ?: return
        val oldWs = friendsWebSocket
        friendsWebSocket = null
        oldWs?.close(1000, "Reconnecting")

        val ws = apiClient.openWebSocket("/ws/friends?token=$token", object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                friendsWebSocket = webSocket
                reconnectAttempt = 0
                scope.launch {
                    fetchFriendsWithStories()
                    fetchIncomingRequests()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (friendsWebSocket != null && webSocket !== friendsWebSocket) return
                try {
                    val msg = apiClient.json.decodeFromString<WsFriendsMessage>(text)
                    when (msg.type) {
                        "request_received",
                        "request_accepted",
                        "request_rejected",
                        "friend_removed",
                        "friends_updated" -> {
                            scope.launch {
                                fetchIncomingRequests()
                                fetchFriendsWithStories()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (friendsWebSocket != null && webSocket !== friendsWebSocket) return
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (friendsWebSocket === webSocket) {
                    friendsWebSocket = null
                }
                if (code != 1000) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (friendsWebSocket === webSocket) {
                    friendsWebSocket = null
                }
                scheduleReconnect()
            }
        })
        friendsWebSocket = ws
    }

    private fun scheduleReconnect() {
        if (!isAuthenticated) return
        reconnectJob?.cancel()
        val backoffMs = minOf(30_000L, 2_000L shl minOf(reconnectAttempt, 4))
        reconnectAttempt += 1
        reconnectJob = scope.launch {
            delay(backoffMs)
            if (isAuthenticated && friendsWebSocket == null) {
                connectFriendsWebSocket()
            }
        }
    }

    override suspend fun sendFriendRequest(playerTag: String): String {
        val cleanTag = playerTag.trim().uppercase().removePrefix("#")
        val body = apiClient.json.encodeToString(FriendRequestDto(playerTag = cleanTag))
        val resp = apiClient.post("/api/friends/request", body) { res ->
            if (res.isBlank()) ActionMessageDto() else apiClient.json.decodeFromString<ActionMessageDto>(res)
        }.getOrThrow()
        fetchFriendsWithStories()
        fetchIncomingRequests()
        return resp.message?.takeIf { it.isNotBlank() } ?: "Friend request sent!"
    }

    override suspend fun acceptFriendRequest(senderId: String) {
        apiClient.post("/api/friends/$senderId/accept", null) { }.getOrThrow()
        fetchFriendsWithStories()
        fetchIncomingRequests()
    }

    override suspend fun rejectFriendRequest(senderId: String) {
        apiClient.post("/api/friends/$senderId/reject", null) { }.getOrThrow()
        fetchIncomingRequests()
    }

    override suspend fun removeFriend(friendId: String) {
        apiClient.delete("/api/friends/$friendId") { }.getOrThrow()
        _friends.value = _friends.value.filter { it.id != friendId }
        fetchFriendsWithStories()
        fetchIncomingRequests()
    }

    override suspend fun refresh() {
        val friendsRes = apiClient.get("/api/friends") { body ->
            apiClient.json.decodeFromString<List<FriendStoryDto>>(body)
        }
        val reqsRes = apiClient.get("/api/friends/requests") { body ->
            apiClient.json.decodeFromString<List<FriendProfileDto>>(body)
        }
        friendsRes.getOrNull()?.let { dtoList ->
            _friends.value = dtoList.map { it.toUiState() }
        }
        reqsRes.getOrNull()?.let { dtoList ->
            _pendingRequests.value = dtoList.map { Profile(id = it.id, displayName = it.displayName, playerTag = it.playerTag) }
        }
        friendsRes.exceptionOrNull()?.let { throw it }
        reqsRes.exceptionOrNull()?.let { throw it }
    }

    override suspend fun fetchIncomingRequests(): List<Profile> {
        val result = apiClient.get("/api/friends/requests") { body ->
            apiClient.json.decodeFromString<List<FriendProfileDto>>(body)
        }
        return result.fold(
            onSuccess = { dtoList ->
                val mapped = dtoList.map { dto ->
                    Profile(
                        id = dto.id,
                        displayName = dto.displayName,
                        playerTag = dto.playerTag
                    )
                }
                _pendingRequests.value = mapped
                mapped
            },
            onFailure = {
                _pendingRequests.value
            }
        )
    }

    override suspend fun fetchFriendsWithStories(): List<FriendUiState> {
        val result = apiClient.get("/api/friends") { body ->
            apiClient.json.decodeFromString<List<FriendStoryDto>>(body)
        }
        return result.fold(
            onSuccess = { dtoList ->
                val mapped = dtoList.map { it.toUiState() }
                _friends.value = mapped
                mapped
            },
            onFailure = {
                _friends.value
            }
        )
    }

    private fun FriendStoryDto.toUiState(): FriendUiState = FriendUiState(
        id = friendId,
        name = displayName,
        playerTag = playerTag,
        lastSessionDuration = durationMinutes,
        lastSessionDate = endTime,
        lastTaskText = lastTaskText,
        friendsSince = acceptedAt ?: "Recently"
    )
}
