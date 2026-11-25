package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.room.FocusRoomRepository
import com.grepho.cozydoubling.features.room.FocusSession
import com.grepho.cozydoubling.features.room.ParticipantPresence
import com.grepho.cozydoubling.features.room.RoomConnectionStatus
import com.grepho.cozydoubling.features.room.RoomSessionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

class FakeFocusRoomRepository(
    private val profileRepository: ProfileRepository
) : FocusRoomRepository {

    private val initialParticipants = listOf(
        ParticipantPresence(
            id = "user_luna_101:session_101",
            name = "Luna",
            activeTaskText = "Reading chapter 4",
            completedTasks = 2,
            totalTasks = 4
        ),
        ParticipantPresence(
            id = "user_oliver_102:session_102",
            name = "Oliver",
            activeTaskText = "Writing essay draft",
            completedTasks = 1,
            totalTasks = 3
        ),
        ParticipantPresence(
            id = "user_maya_103:session_103",
            name = "Maya",
            activeTaskText = "Solving math problem set",
            completedTasks = 3,
            totalTasks = 5
        )
    )

    private val _participants = MutableStateFlow(initialParticipants)
    private val _broadcastFlow = MutableSharedFlow<ParticipantPresence>(replay = 0)
    private val sessions = mutableMapOf<String, FocusSession>()
    private val sessionStartTimes = mutableMapOf<String, Long>()

    override fun joinRoom(presenceKey: String): Flow<List<ParticipantPresence>> {
        return _participants.asStateFlow()
    }

    override suspend fun subscribe() {
        // Connected immediately in offline mode
    }

    override fun getChannelStatus(): Flow<RoomConnectionStatus> {
        return flowOf(RoomConnectionStatus.CONNECTED)
    }

    override suspend fun broadcastPresence(presence: ParticipantPresence) {
        _broadcastFlow.emit(presence)
    }

    override fun listenForBroadcasts(): Flow<ParticipantPresence> {
        return _broadcastFlow.asSharedFlow()
    }

    override suspend fun updatePresence(presence: ParticipantPresence) {
        _participants.value = _participants.value.filter { it.id != presence.id } + presence
    }

    override suspend fun leaveRoom() {
        // In-memory room clean up
    }

    override suspend fun startSession(): String? {
        val sessionId = "session_${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()
        sessionStartTimes[sessionId] = now
        val session = FocusSession(
            id = sessionId,
            userId = profileRepository.profile.value?.id ?: "user_cozy_001",
            startTime = "2026-11-20T12:00:00Z",
            endTime = null,
            tasksCompleted = 0,
            earnedLeaves = 0,
            isProcessed = false,
            durationMinutes = 0
        )
        sessions[sessionId] = session
        return sessionId
    }

    override suspend fun finishSession(
        sessionId: String,
        tasksCompleted: Int,
        taskText: String
    ) {
        val startTime = sessionStartTimes[sessionId] ?: System.currentTimeMillis()
        val elapsedMs = System.currentTimeMillis() - startTime
        // Simulated duration: default 25 minutes if quickly clicked, or elapsed minutes
        val durationMinutes = maxOf(25, (elapsedMs / 60000).toInt())
        val earnedLeaves = maxOf(1L, (durationMinutes * 1L) + (tasksCompleted * 3L))

        val finishedSession = FocusSession(
            id = sessionId,
            userId = profileRepository.profile.value?.id ?: "user_cozy_001",
            startTime = "2026-11-20T12:00:00Z",
            endTime = "2026-11-20T12:25:00Z",
            tasksCompleted = tasksCompleted,
            earnedLeaves = earnedLeaves,
            isProcessed = true,
            durationMinutes = durationMinutes
        )
        sessions[sessionId] = finishedSession

        profileRepository.updateLeaves(earnedLeaves)
        profileRepository.updateFocusMinutes(durationMinutes.toLong())
        profileRepository.refreshProfile()
    }

    override suspend fun fetchSession(sessionId: String): FocusSession? {
        return sessions[sessionId] ?: FocusSession(
            id = sessionId,
            userId = "user_cozy_001",
            startTime = "2026-11-20T12:00:00Z",
            endTime = "2026-11-20T12:25:00Z",
            tasksCompleted = 2,
            earnedLeaves = 31,
            isProcessed = true,
            durationMinutes = 25
        )
    }

    override fun observeSessionEvents(): Flow<RoomSessionEvent> = emptyFlow()
}
