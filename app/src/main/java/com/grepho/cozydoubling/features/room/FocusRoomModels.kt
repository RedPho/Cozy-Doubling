package com.grepho.cozydoubling.features.room

data class FocusTask(
    val id: String,
    val text: String,
    val isCompleted: Boolean = false
)

data class RoomParticipant(
    val id: String,
    val name: String,
    val activeTaskText: String,
    val completedTasks: Int,
    val totalTasks: Int
)