package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
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
                    }
                }
            }
        }
    }

    private suspend fun refreshBlockedUsers() {
        val result = apiClient.get("/api/safety/blocks") { body ->
            apiClient.json.decodeFromString<BlockedUsersDto>(body)
        }
        result.onSuccess { dto ->
            _blockedUserIds.value = dto.blockedUserIds.toSet()
        }
    }

    private fun connectSafetyWebSocket() {
        val token = apiClient.authToken ?: return
        safetyWebSocket?.close(1000, "Reconnecting")

        safetyWebSocket = apiClient.openWebSocket("/ws/safety?token=$token", object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
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
        })
    }

    override suspend fun blockUser(targetId: String) {
        val body = apiClient.json.encodeToString(BlockUserRequestDto(targetId = targetId))
        _blockedUserIds.update { it + targetId }
        apiClient.post("/api/safety/block", body) { }
    }

    override suspend fun reportUser(targetId: String, reason: String) {
        val body = apiClient.json.encodeToString(ReportUserRequestDto(targetId = targetId, reason = reason))
        apiClient.post("/api/safety/report", body) { }
    }
}
