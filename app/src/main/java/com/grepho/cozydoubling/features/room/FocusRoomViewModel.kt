package com.grepho.cozydoubling.features.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.network.ConnectionStateManager
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
        startRoom()

        // 3. Observe the "Other Participants" directly from the Repository
        roomRepository.otherParticipants
            .onEach { participants ->
                _uiState.update { it.copy(otherParticipants = participants) }
            }
            .launchIn(viewModelScope)

        // 4. Listen for Retry Events
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

            // Always try to join room (handles re-joining channel)
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

    fun finishWork(onComplete: (String) -> Unit) {
        val sessionId = currentSessionId ?: return
        val state = _uiState.value

        // Find the text of your current active focus
        val lastTask = state.tasks.find { it.id == state.activeTaskId }?.text ?: "Focusing"

        viewModelScope.launch {
            try {
                // Pass the text to the repository here!
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
        // We use GlobalScope here because the ViewModelScope is already cancelled
        // and we MUST ensure this network call finishes.
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                println("DEBUG: RoomViewModel - Starting global cleanup...")
                roomRepository.leaveRoom()
                println("DEBUG: RoomViewModel - Channel cleaned up successfully.")
            } catch (e: Exception) {
                println("DEBUG: Cleanup error: ${e.message}")
            }
        }
    }
}