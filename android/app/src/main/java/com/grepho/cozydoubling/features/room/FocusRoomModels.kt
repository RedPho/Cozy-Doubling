package com.grepho.cozydoubling.features.room

import kotlinx.serialization.Serializable

@Serializable
data class FocusTask(
    val id: String,
    val text: String,
    val isCompleted: Boolean = false
)

/**
 * Presence payload — full participant identity + task state.
 * Tracked via room presence updates and received via participant streams.
 * Updated throttled (at most once per ~10 s) to avoid flooding.
 */
data class ParticipantPresence(
    val id: String,
    val name: String = "Cozy Doubler",
    val activeTaskText: String? = "",
    val completedTasks: Int = 0,
    val totalTasks: Int = 0
) {
    val displayTaskText: String get() = if (activeTaskText.isNullOrBlank()) "Focusing" else activeTaskText
}


data class RoomParticipant(
    val id: String,
    val name: String,
    val activeTaskText: String,
    val completedTasks: Int,
    val totalTasks: Int,
)

data class FocusSession(
    val id: String,
    val userId: String,
    val startTime: String,
    val endTime: String? = null,
    val tasksCompleted: Int = 0,
    val earnedLeaves: Long = 0,
    val isProcessed: Boolean = false,
    val durationMinutes: Int = 0
)

data class FocusRoomUiState(
    val tasks: List<FocusTask> = emptyList(),
    val activeTaskId: String? = null,
    val otherParticipants: List<RoomParticipant> = emptyList(),
    val sessionStartEpochMs: Long = System.currentTimeMillis()
)
