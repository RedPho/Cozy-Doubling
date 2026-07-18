package com.grepho.cozydoubling.features.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FocusTask(
    val id: String,
    val text: String,
    val isCompleted: Boolean = false
)
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
@Serializable
data class RoomParticipant(
    val id: String,
    val name: String,
    val activeTaskText: String,
    val completedTasks: Int,
    val totalTasks: Int
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
    @SerialName("is_processed")
    val isProcessed: Boolean = false
)

@Serializable
data class ParticipantAction(
    val id: String,
    @SerialName("active_task")
    val activeTaskText: String,
    @SerialName("completed_tasks")
    val completedTasks: Int,
    @SerialName("total_tasks")
    val totalTasks: Int
)

@Serializable
data class FinishSessionParams(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("tasks_done")
    val tasksDone: Int
)


data class FocusRoomUiState(
    val tasks: List<FocusTask> = emptyList(),
    val activeTaskId: String? = null,
    val otherParticipants: List<RoomParticipant> = emptyList()
)