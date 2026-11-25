package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.safety.BlockedUser
import com.grepho.cozydoubling.core.safety.SafetyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class RemoteSafetyRepository(
    private val apiClient: ApiClient,
    private val authRepository: AuthRepository
) : SafetyRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    override val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<BlockedUser>>(emptyList())
    override val blockedUsers: StateFlow<List<BlockedUser>> = _blockedUsers.asStateFlow()

    private var safetyWebSocket: WebSocket? = null

    init {
        scope.launch {
            authRepository.authStatus.collect { status ->
                when (status) {
                    is AuthStatus.Authenticated -> {
                        refreshBlockedUsers()
                        connectSafetyWebSocket()
                    }
                    else -> {
                        safetyWebSocket?.close(1000, "Signed out")
                        safetyWebSocket = null
                        _blockedUserIds.value = emptySet()
                        _blockedUsers.value = emptyList()
                    }
                }
            }
        }
    }

    override suspend fun refreshBlockedUsers() {
        val idsResult = apiClient.get("/api/safety/blocks") { body ->
            apiClient.json.decodeFromString<BlockedUsersDto>(body)
        }
        idsResult.onSuccess { dto ->
            _blockedUserIds.value = dto.blockedUserIds.toSet()
        }

        val profilesResult = apiClient.get("/api/safety/blocked-profiles") { body ->
            apiClient.json.decodeFromString<List<BlockedProfileDto>>(body)
        }
        profilesResult.onSuccess { list ->
            _blockedUsers.value = list.map { it.toDomain() }
        }
    }

    private fun connectSafetyWebSocket() {
        val token = apiClient.authToken ?: return
        val oldWs = safetyWebSocket
        safetyWebSocket = null
        oldWs?.close(1000, "Reconnecting")

        val ws = apiClient.openWebSocket("/ws/safety?token=$token", object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                safetyWebSocket = webSocket
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (safetyWebSocket != null && webSocket !== safetyWebSocket) return
                try {
                    val msg = apiClient.json.decodeFromString<WsSafetyMessage>(text)
                    when (msg.type) {
                        "block_added" -> {
                            msg.blockedId?.let { id ->
                                _blockedUserIds.update { it + id }
                            }
                        }
                        "block_removed" -> {
                            msg.blockedId?.let { id ->
                                _blockedUserIds.update { it - id }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (safetyWebSocket != null && webSocket !== safetyWebSocket) return
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (safetyWebSocket === webSocket) {
                    safetyWebSocket = null
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (safetyWebSocket === webSocket) {
                    safetyWebSocket = null
                }
            }
        })
        safetyWebSocket = ws
    }

    override suspend fun blockUser(targetId: String) {
        val previous = _blockedUserIds.value
        val body = apiClient.json.encodeToString(BlockUserRequestDto(targetId = targetId))
        _blockedUserIds.update { it + targetId }
        val res = apiClient.post("/api/safety/block", body) { }
        if (res.isFailure) {
            _blockedUserIds.value = previous
            res.getOrThrow()
        }
        refreshBlockedUsers()
    }

    override suspend fun unblockUser(targetId: String) {
        val body = apiClient.json.encodeToString(BlockUserRequestDto(targetId = targetId))
        apiClient.post("/api/safety/unblock", body) { }.getOrThrow()
        _blockedUsers.update { list -> list.filter { it.id != targetId } }
        refreshBlockedUsers()
    }

    override suspend fun reportUser(targetId: String, reason: String) {
        val body = apiClient.json.encodeToString(ReportUserRequestDto(targetId = targetId, reason = reason))
        apiClient.post("/api/safety/report", body) { }.getOrThrow()
    }
}
