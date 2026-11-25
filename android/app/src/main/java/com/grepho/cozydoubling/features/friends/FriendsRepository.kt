package com.grepho.cozydoubling.features.friends

import com.grepho.cozydoubling.core.profile.Profile
import kotlinx.coroutines.flow.StateFlow

interface FriendsRepository {
    val friends: StateFlow<List<FriendUiState>>
    val pendingRequests: StateFlow<List<Profile>>

    suspend fun sendFriendRequest(playerTag: String)
    suspend fun acceptFriendRequest(senderId: String)
    suspend fun rejectFriendRequest(senderId: String)
    suspend fun removeFriend(friendId: String)
    suspend fun fetchIncomingRequests(): List<Profile>
    suspend fun fetchFriendsWithStories(): List<FriendUiState>
}
