package com.grepho.cozydoubling.features.room

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// We create a single state class to hold everything the room needs
data class FocusRoomUiState(
    val tasks: List<FocusTask> = emptyList(),
    val activeTaskId: String? = null,
    val otherParticipants: List<RoomParticipant> = emptyList()
)

class FocusRoomViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FocusRoomUiState())
    val uiState: StateFlow<FocusRoomUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        val initialTasks = listOf(
            FocusTask("1", "Read chapter 4"),
            FocusTask("2", "Reply to emails", isCompleted = true),
            FocusTask("3", "Write introduction")
        )

        _uiState.value = FocusRoomUiState(
            tasks = initialTasks,
            activeTaskId = initialTasks.firstOrNull { !it.isCompleted }?.id,
            otherParticipants = listOf(
                RoomParticipant("1", "Alex", "Writing emails", 3, 7),
                RoomParticipant("2", "Jamie", "Studying Math", 1, 4)
            )
        )
    }

    fun onTaskClick(taskId: String) {
        _uiState.update { it.copy(activeTaskId = taskId) }
    }

    fun onTaskToggleStatus(taskId: String) {
        _uiState.update { state ->
            val updatedTasks = state.tasks.map {
                if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
            }
            state.copy(tasks = updatedTasks)
        }
    }

    fun onAddTask(text: String) {
        if (text.isBlank()) return

        val newTask = FocusTask(
            id = System.currentTimeMillis().toString(),
            text = text
        )

        _uiState.update { state ->
            val updatedTasks = state.tasks + newTask
            state.copy(
                tasks = updatedTasks,
                activeTaskId = state.activeTaskId ?: newTask.id // Set as active if none exists
            )
        }
    }
}