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

    // Throttling state for Presence (track)
    private var lastPresenceSyncTime = 0L
    private var pendingPresenceJob: Job? = null
    private val SYNC_INTERVAL_MS = 10_000L // 10 seconds for Presence track

    init {
        startRoom()
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
                val broadcastFlow = roomRepository.listenForBroadcasts()

                val myId = ProfileRepository.profile.value?.id

                // Monitoring connection status
                viewModelScope.launch {
                    roomRepository.getChannelStatus()
                        .onEach { status ->
                            println("DEBUG: FocusRoomViewModel - Channel status update: $status")
                        }
                        .filter { it == RealtimeChannel.Status.SUBSCRIBED }
                        .collect {
                            println("DEBUG: FocusRoomViewModel - Channel SUBSCRIBED, syncing initial presence...")
                            syncWithOthers()
                        }
                }

                // 3. Process participants: Presence (Stable) + Broadcast (Instant)
                // We use a scan to accumulate broadcast updates so they aren't lost when Presence refreshes
                val latestBroadcasts = broadcastFlow
                    .scan(emptyMap<String, ParticipantPresence>()) { acc, update ->
                        acc + (update.id to update)
                    }
                    .onStart { emit(emptyMap()) } // Unblock combine for initial render

                presenceFlow
                    .map { list -> list.associateBy { it.id } }
                    .combine(latestBroadcasts) { presenceMap, broadcasts ->
                        // Broadcasts override Presence until the next stable 'track' update
                        presenceMap + broadcasts
                    }
                    .combine(SafetyRepository.blockedUserIds) { participantsMap, blockedIds ->
                        participantsMap.values.filter { it.id !in blockedIds && it.id != myId }
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

    private fun syncWithOthers() {
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

            broadcastUpdate(presence)
        } catch (e: Exception) {
            println("DEBUG: FocusRoomViewModel - syncWithOthers error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun broadcastUpdate(presence: ParticipantPresence) {
        viewModelScope.launch {
            try {
                // 1. Instant Path: Broadcast to everyone currently in the room
                roomRepository.broadcastPresence(presence)

                // 2. Throttled Path: Update stable Presence (track) at most once per 10s
                val now = System.currentTimeMillis()
                
                // Cancel any pending sync since we have a fresh one
                pendingPresenceJob?.cancel()

                if (now - lastPresenceSyncTime > SYNC_INTERVAL_MS) {
                    // It's been a while, sync presence immediately
                    lastPresenceSyncTime = now
                    roomRepository.updatePresence(presence)
                } else {
                    // Frequent update, schedule a trailing sync
                    pendingPresenceJob = launch {
                        delay(7.seconds)
                        lastPresenceSyncTime = System.currentTimeMillis()
                        roomRepository.updatePresence(presence)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }
        }
    }

    private fun triggerSync() {
        viewModelScope.launch {
            syncWithOthers()
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
