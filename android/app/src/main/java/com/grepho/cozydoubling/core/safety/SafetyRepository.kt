package com.grepho.cozydoubling.core.safety

import kotlinx.coroutines.flow.StateFlow

interface SafetyRepository {
    val blockedUserIds: StateFlow<Set<String>>
    suspend fun blockUser(targetId: String)
    suspend fun reportUser(targetId: String, reason: String)
}
