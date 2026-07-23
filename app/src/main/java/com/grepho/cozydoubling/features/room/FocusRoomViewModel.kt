package com.grepho.cozydoubling.features.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

class FocusRoomViewModel : ViewModel() {

    private val roomRepository = FocusRoomRepository()

    private val _uiState = MutableStateFlow(FocusRoomUiState())
    val uiState: StateFlow<FocusRoomUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null

    // Used to throttle presence updates
    private val syncIntent = MutableSharedFlow<Unit>(replay = 0)
    private var lastSyncTime = 0L

    init {
        startRoom()

        // Debounced sync for rapid changes
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            syncIntent
                .debounce(7.seconds)
                .collect {
                    syncWithOthers()
                }
        }
    }

    private fun startRoom() {
        viewModelScope.launch {
            try {
                println("DEBUG: FocusRoomViewModel - Starting room join sequence")
                // 1. Start session in DB
                val sessionId = roomRepository.startSession()
                currentSessionId = sessionId
                println("DEBUG: FocusRoomViewModel - Session ID: $sessionId")

                // 2. Join Realtime room
                val presenceFlow = roomRepository.joinRoom()

                val myId = ProfileRepository.profile.value?.id

                // Initial and Re-sync logic:
                // We sync every time the channel becomes SUBSCRIBED.
                // This handles initial join AND automatic reconnections.
                viewModelScope.launch {
                    roomRepository.getChannelStatus()
                        .onEach { status ->
                            println("DEBUG: FocusRoomViewModel - Channel status update: $status")
                        }
                        .filter { it == RealtimeChannel.Status.SUBSCRIBED }
                        .collect {
                            println("DEBUG: FocusRoomViewModel - Channel SUBSCRIBED, syncing presence...")
                            syncWithOthers()
                        }
                }

                presenceFlow
                    .combine(SafetyRepository.blockedUserIds) { participants, blockedIds ->
                        // Heavy filtering and ID mapping done on background thread
                        participants.filter { it.id !in blockedIds && it.id != myId }
                    }
                    .flowOn(Dispatchers.Default) 
                    .collect { participants ->
                        println("DEBUG: FocusRoomViewModel - Updating UI with ${participants.size} other participants")
                        _uiState.update { state ->
                            state.copy(otherParticipants = participants.map { p ->
                                RoomParticipant(
                                    id = p.id,
                                    name = p.name,
                                    activeTaskText = p.activeTaskText,
                                    completedTasks = p.completedTasks,
                                    totalTasks = p.totalTasks,
                                )
                            })
                        }
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                println("DEBUG: FocusRoomViewModel - startRoom critical error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun syncWithOthers() {
        try {
            val profile = ProfileRepository.profile.value ?: run {
                println("DEBUG: FocusRoomViewModel - syncWithOthers: No profile found")
                return
            }
            val state = _uiState.value
            val activeTask = state.tasks.find { it.id == state.activeTaskId }

            val presence = ParticipantPresence(
                id = profile.id,
                name = profile.displayName,
                activeTaskText = activeTask?.text ?: "No active task",
                completedTasks = state.tasks.count { it.isCompleted },
                totalTasks = state.tasks.size
            )

            println("DEBUG: FocusRoomViewModel - Sending presence update for ${profile.displayName}")
            roomRepository.updatePresence(presence)
            lastSyncTime = System.currentTimeMillis()
        } catch (e: Exception) {
            println("DEBUG: FocusRoomViewModel - syncWithOthers error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun triggerSync() {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            if (now - lastSyncTime > 7000L) {
                // First change in a while -> Sync immediately
                syncWithOthers()
            } else {
                // Frequent changes -> Use debounce to sync only the last one
                syncIntent.emit(Unit)
            }
        }
    }

    fun onTaskClick(taskId: String) {
        _uiState.update { it.copy(activeTaskId = taskId) }
        triggerSync()
    }

    fun onTaskToggleStatus(taskId: String) {
        _uiState.update { state ->
            val updatedTasks = state.tasks.map {
                if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
            }
            state.copy(tasks = updatedTasks)
        }
        triggerSync()
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
        triggerSync()
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
