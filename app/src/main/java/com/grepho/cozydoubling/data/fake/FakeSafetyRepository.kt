package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.safety.SafetyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeSafetyRepository : SafetyRepository {

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    override val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    override suspend fun blockUser(targetId: String) {
        _blockedUserIds.update { it + targetId }
    }

    override suspend fun reportUser(targetId: String, reason: String) {
        // Log report locally for offline testing
        println("FakeSafetyRepository: User $targetId reported with reason: $reason")
    }
}
