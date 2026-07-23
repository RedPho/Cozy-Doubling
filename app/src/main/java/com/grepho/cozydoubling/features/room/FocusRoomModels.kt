package com.grepho.cozydoubling.features.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FocusTask(
    val id: String,
    val text: String,
    val isCompleted: Boolean = false
)

/**
 * Presence payload — full participant identity + task state.
 * Tracked via channel.track() and received via presenceDataFlow.
 * Updated throttled (at most once per ~10 s) to avoid flooding.
 */
@Serializable
data class ParticipantPresence(
    val id: String,
    val name: String,
    @SerialName("active_task")
    val activeTaskText: String,
    @SerialName("completed_tasks")
    val completedTasks: Int,
    @SerialName("total_tasks")
    val totalTasks: Int
)


data class RoomParticipant(
    val id: String,
    val name: String,
    val activeTaskText: String,
    val completedTasks: Int,
    val totalTasks: Int,
)

@Serializable
data class FocusSession(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("start_time")
    val startTime: String,
    @SerialName("end_time")
    val endTime: String? = null,
    @SerialName("tasks_completed")
    val tasksCompleted: Int = 0,
    @SerialName("earned_leaves")
    val earnedLeaves: Long = 0,
    @SerialName("is_processed")
    val isProcessed: Boolean = false,
    @SerialName("duration_minutes")
    val durationMinutes: Int = 0
)

@Serializable
data class FinishSessionParams(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("tasks_done")
    val tasksDone: Int,
    @SerialName("task_text")
    val taskText: String
)

@Serializable
data class UserReport(
    @SerialName("reported_id") val reportedId: String,
    @SerialName("reason") val reason: String
)

@Serializable
data class UserBlock(
    @SerialName("blocked_id") val blockedId: String
)

data class FocusRoomUiState(
    val tasks: List<FocusTask> = emptyList(),
    val activeTaskId: String? = null,
    val otherParticipants: List<RoomParticipant> = emptyList()
)
