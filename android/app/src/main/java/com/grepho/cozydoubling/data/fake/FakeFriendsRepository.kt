package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import com.grepho.cozydoubling.features.friends.FriendUiState
import com.grepho.cozydoubling.features.friends.FriendsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeFriendsRepository(
    private val profileRepository: ProfileRepository,
    private val safetyRepository: SafetyRepository? = null
) : FriendsRepository {

    private val _friends = MutableStateFlow(
        listOf(
            FriendUiState(
                id = "friend_sam",
                name = "Sam",
                playerTag = "SAMM9999",
                lastSessionDuration = 45,
                lastSessionDate = "2 hours ago",
                lastTaskText = "Reviewing system architecture",
                friendsSince = "2026-02-01"
            ),
            FriendUiState(
                id = "friend_aria",
                name = "Aria",
                playerTag = "ARIA7777",
                lastSessionDuration = 60,
                lastSessionDate = "Yesterday",
                lastTaskText = "Writing product roadmap",
                friendsSince = "2026-02-10"
            ),
            FriendUiState(
                id = "friend_milo",
                name = "Milo",
                playerTag = "MILO5555",
                lastSessionDuration = 30,
                lastSessionDate = "3 days ago",
                lastTaskText = "Practicing French vocabulary",
                friendsSince = "2026-02-14"
            )
        )
    )
    override val friends: StateFlow<List<FriendUiState>> = _friends.asStateFlow()

    private val _pendingRequests = MutableStateFlow(
        listOf(
            Profile(
                id = "pending_robin",
                displayName = "Robin",
                playerTag = "ROBN1111"
            )
        )
    )
    override val pendingRequests: StateFlow<List<Profile>> = _pendingRequests.asStateFlow()

    override suspend fun sendFriendRequest(playerTag: String): String {
        val cleanTag = playerTag.trim().uppercase().removePrefix("#")
        if (cleanTag.isBlank()) {
            throw IllegalArgumentException("Please enter a friend code.")
        }
        val myTag = profileRepository.profile.value?.playerTag?.uppercase()
        if (myTag != null && cleanTag == myTag) {
            throw IllegalArgumentException("You can't add yourself!")
        }

        val blockedUsers = safetyRepository?.blockedUsers?.value.orEmpty()
        val blockedIds = safetyRepository?.blockedUserIds?.value.orEmpty()
        val isBlocked = blockedUsers.any {
            it.playerTag.equals(cleanTag, ignoreCase = true) ||
                it.id.equals(cleanTag, ignoreCase = true) ||
                it.id == "friend_$cleanTag"
        } || blockedIds.any {
            it.equals(cleanTag, ignoreCase = true) ||
                it.removePrefix("friend_").equals(cleanTag, ignoreCase = true) ||
                it.take(8).equals(cleanTag, ignoreCase = true)
        }
        if (isBlocked) {
            throw IllegalStateException("You have blocked this user. Unblock them in Settings to add them.")
        }

        if (_friends.value.any { it.playerTag.equals(cleanTag, ignoreCase = true) }) {
            throw IllegalStateException("Already friends!")
        }

        val pendingMatch = _pendingRequests.value.find { it.playerTag.equals(cleanTag, ignoreCase = true) }
        if (pendingMatch != null) {
            acceptFriendRequest(pendingMatch.id)
            return "Friend request accepted!"
        }

        // Add mock friend for offline testing
        val newFriend = FriendUiState(
            id = "friend_$cleanTag",
            name = "Cozy Buddy ($cleanTag)",
            playerTag = cleanTag,
            lastSessionDuration = 25,
            lastSessionDate = "Just now",
            lastTaskText = "Getting things done",
            friendsSince = "Today"
        )
        _friends.update { it + newFriend }
        return "Friend request sent!"
    }

    override suspend fun acceptFriendRequest(senderId: String) {
        val request = _pendingRequests.value.find { it.id == senderId } ?: return
        _pendingRequests.update { it.filter { p -> p.id != senderId } }
        val newFriend = FriendUiState(
            id = request.id,
            name = request.displayName,
            playerTag = request.playerTag,
            lastSessionDuration = 20,
            lastSessionDate = "Today",
            lastTaskText = "Focusing cozy",
            friendsSince = "Today"
        )
        _friends.update { it + newFriend }
    }

    override suspend fun rejectFriendRequest(senderId: String) {
        _pendingRequests.update { it.filter { p -> p.id != senderId } }
    }

    override suspend fun removeFriend(friendId: String) {
        _friends.update { list -> list.filter { it.id != friendId } }
    }

    override suspend fun refresh() {
        // Fake data is already live in memory
    }

    override suspend fun fetchIncomingRequests(): List<Profile> {
        return pendingRequests.value
    }

    override suspend fun fetchFriendsWithStories(): List<FriendUiState> {
        return friends.value
    }
}
