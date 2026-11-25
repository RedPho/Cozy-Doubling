package com.grepho.cozydoubling.core.safety

import kotlinx.coroutines.flow.StateFlow

data class BlockedUser(
    val id: String,
    val displayName: String,
    val playerTag: String
)

interface SafetyRepository {
    val blockedUserIds: StateFlow<Set<String>>
    val blockedUsers: StateFlow<List<BlockedUser>>
    suspend fun refreshBlockedUsers()
    suspend fun blockUser(targetId: String)
    suspend fun unblockUser(targetId: String)
    suspend fun reportUser(targetId: String, reason: String)
}
