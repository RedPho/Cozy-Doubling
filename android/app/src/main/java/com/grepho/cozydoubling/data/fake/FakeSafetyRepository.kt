package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.safety.BlockedUser
import com.grepho.cozydoubling.core.safety.SafetyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeSafetyRepository : SafetyRepository {

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    override val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<BlockedUser>>(emptyList())
    override val blockedUsers: StateFlow<List<BlockedUser>> = _blockedUsers.asStateFlow()

    override suspend fun refreshBlockedUsers() {
        // Offline fake state is already authoritative in memory
    }

    override suspend fun blockUser(targetId: String) {
        _blockedUserIds.update { it + targetId }
        _blockedUsers.update { current ->
            if (current.any { it.id == targetId }) current
            else current + BlockedUser(
                id = targetId,
                displayName = "Blocked User",
                playerTag = targetId.take(8).uppercase()
            )
        }
    }

    override suspend fun unblockUser(targetId: String) {
        _blockedUserIds.update { it - targetId }
        _blockedUsers.update { current -> current.filter { it.id != targetId } }
    }

    override suspend fun reportUser(targetId: String, reason: String) {
        // Log report locally for offline testing
        println("FakeSafetyRepository: User $targetId reported with reason: $reason")
    }
}
