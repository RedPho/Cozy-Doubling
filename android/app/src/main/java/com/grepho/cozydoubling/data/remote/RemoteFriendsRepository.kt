package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.friends.FriendUiState
import com.grepho.cozydoubling.features.friends.FriendsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class RemoteFriendsRepository(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository
) : FriendsRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _friends = MutableStateFlow<List<FriendUiState>>(emptyList())
    override val friends: StateFlow<List<FriendUiState>> = _friends.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<Profile>>(emptyList())
    override val pendingRequests: StateFlow<List<Profile>> = _pendingRequests.asStateFlow()

    init {
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

    override suspend fun sendFriendRequest(playerTag: String) {
        val cleanTag = playerTag.trim().uppercase().removePrefix("#")
        val body = apiClient.json.encodeToString(FriendRequestDto(playerTag = cleanTag))
        apiClient.post("/api/friends/request", body) { }
        fetchFriendsWithStories()
        fetchIncomingRequests()
    }

    override suspend fun acceptFriendRequest(senderId: String) {
        apiClient.post("/api/friends/$senderId/accept", null) { }
        fetchFriendsWithStories()
        fetchIncomingRequests()
    }

    override suspend fun rejectFriendRequest(senderId: String) {
        apiClient.post("/api/friends/$senderId/reject", null) { }
        fetchIncomingRequests()
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
                emptyList()
            }
        )
    }

    override suspend fun fetchFriendsWithStories(): List<FriendUiState> {
        val result = apiClient.get("/api/friends") { body ->
            apiClient.json.decodeFromString<List<FriendStoryDto>>(body)
        }
        return result.fold(
            onSuccess = { dtoList ->
                val mapped = dtoList.map { dto ->
                    FriendUiState(
                        id = dto.friendId,
                        name = dto.displayName,
                        playerTag = dto.playerTag,
                        lastSessionDuration = dto.durationMinutes,
                        lastSessionDate = dto.endTime ?: "Just now",
                        lastTaskText = dto.lastTaskText ?: "Focusing",
                        friendsSince = dto.acceptedAt ?: "Recently"
                    )
                }
                _friends.value = mapped
                mapped
            },
            onFailure = {
                emptyList()
            }
        )
    }
}
