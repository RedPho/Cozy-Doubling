package com.grepho.cozydoubling.features.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.network.ConnectionStateManager
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class FocusRoomViewModel : ViewModel() {

    private val roomRepository = FocusRoomRepository(viewModelScope)

    private val _uiState = MutableStateFlow(FocusRoomUiState())
    val uiState: StateFlow<FocusRoomUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null

    init {
        startRoom()

        roomRepository.otherParticipants
            .onEach { participants ->
                _uiState.update { it.copy(otherParticipants = participants) }
            }
            .launchIn(viewModelScope)

        ConnectionStateManager.retryEvents
            .onEach { startRoom() }
            .launchIn(viewModelScope)
    }

    private fun startRoom() {
        viewModelScope.launch {
            val currentProfile = ProfileRepository.profile.filterNotNull().first()
            if (currentSessionId == null) {
                currentSessionId = roomRepository.startSession()
            }

            val state = _uiState.value
            val initialPresence = ParticipantPresence(
                id = currentProfile.id,
                name = currentProfile.displayName,
                activeTaskText = state.tasks.find { it.id == state.activeTaskId }?.text ?: "Focusing...",
                completedTasks = state.tasks.count { it.isCompleted },
                totalTasks = state.tasks.size
            )

            roomRepository.joinRoom(initialPresence)
        }
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

    fun onBlockUser(userId: String) {
        viewModelScope.launch {
            SafetyRepository.blockUser(userId)
        }
    }

    fun onReportUser(userId: String, reason: String) {
        viewModelScope.launch {
            SafetyRepository.reportUser(userId, reason)
        }
    }

    fun finishWork(onComplete: (String) -> Unit) {
        val sessionId = currentSessionId ?: return
        val state = _uiState.value
        val lastTask = state.tasks.find { it.id == state.activeTaskId }?.text ?: "Focusing"

        viewModelScope.launch {
            try {
                roomRepository.finishSession(sessionId, state.tasks.count { it.isCompleted }, lastTask)
                ProfileRepository.refreshProfile()
                onComplete(sessionId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled at this point, so we use a short-lived
        // scope to ensure the leave call completes before the VM is destroyed.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                roomRepository.leaveRoom()
            } catch (e: Exception) {
                println("DEBUG: leaveRoom cleanup error: ${e.message}")
            }
        }
    }
}
