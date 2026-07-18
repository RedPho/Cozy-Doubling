package com.grepho.cozydoubling.features.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FocusRoomViewModel : ViewModel() {

    // 1. Initialize Repo with the ViewModel's scope
    private val roomRepository = FocusRoomRepository(viewModelScope)

    private val _uiState = MutableStateFlow(FocusRoomUiState())
    val uiState: StateFlow<FocusRoomUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null

    init {
        // 2. Start the Room Logic
        viewModelScope.launch {
            currentSessionId = roomRepository.startSession()
            roomRepository.joinRoom()
        }

        // 3. Observe the "Other Participants" directly from the Repository
        roomRepository.otherParticipants
            .onEach { participants ->
                _uiState.update { it.copy(otherParticipants = participants) }
            }
            .launchIn(viewModelScope)
    }

    private fun syncWithOthers() {
        val currentProfile = ProfileRepository.profile.value ?: return
        val state = _uiState.value

        val action = ParticipantAction(
            id = currentProfile.id,
            activeTaskText = state.tasks.find { it.id == state.activeTaskId }?.text ?: "Focusing...",
            completedTasks = state.tasks.count { it.isCompleted },
            totalTasks = state.tasks.size
        )
        roomRepository.broadcastUpdate(action)
    }

    fun onTaskClick(taskId: String) {
        _uiState.update { it.copy(activeTaskId = taskId) }
        syncWithOthers()
    }

    fun onTaskToggleStatus(taskId: String) {
        _uiState.update { state ->
            val updatedTasks = state.tasks.map {
                if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
            }
            state.copy(tasks = updatedTasks)
        }
        syncWithOthers()
    }

    fun onAddTask(text: String) {
        if (text.isBlank()) return
        val newTask = FocusTask(id = System.currentTimeMillis().toString(), text = text)

        _uiState.update { state ->
            val updatedTasks = state.tasks + newTask
            state.copy(
                tasks = updatedTasks,
                activeTaskId = state.activeTaskId ?: newTask.id
            )
        }
        syncWithOthers()
    }

    fun finishWork(onComplete: () -> Unit) {
        val sessionId = currentSessionId ?: return
        val completedCount = _uiState.value.tasks.count { it.isCompleted }

        viewModelScope.launch {
            try {
                roomRepository.finishSession(sessionId, completedCount)
                ProfileRepository.refreshProfile()
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 4. Lean Cleanup: Just tell the repo to leave
        viewModelScope.launch {
            roomRepository.leaveRoom()
        }
    }
}