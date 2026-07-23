package com.grepho.cozydoubling.features.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds
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
                println("DEBUG: FocusRoomViewModel - [1/4] Starting room setup sequence")
                // 1. Start session in DB
                val sessionId = roomRepository.startSession()
                currentSessionId = sessionId
                println("DEBUG: FocusRoomViewModel - [2/4] Session ID: $sessionId")

                // 2. Prepare flows (DO NOT subscribe yet)
                val presenceFlow = roomRepository.joinRoom()
                val broadcastFlow = roomRepository.listenForBroadcasts()

                // 3. Setup Broadcast Processor (Instant Overrides)
                val latestBroadcasts = broadcastFlow
                    .scan(emptyMap<String, ParticipantPresence>()) { acc, update ->
                        acc + (update.id to update)
                    }
                    .onStart { emit(emptyMap()) }

                // 4. START LISTENING (Parallel coroutine)
                // This ensures we are listening BEFORE the subscription starts.
                launch {
                    println("DEBUG: FocusRoomViewModel - [3/4] Listener coroutine active")
                    presenceFlow
                        .onEach { list -> println("DEBUG: FocusRoomViewModel - RECEIVED PRESENCE: ${list.size} users") }
                        .map { list -> list.associateBy { it.id } }
                        .combine(latestBroadcasts) { presenceMap, broadcasts ->
                            presenceMap.mapValues { (id, presence) -> broadcasts[id] ?: presence }
                        }
                        .combine(SafetyRepository.blockedUserIds) { participantsMap, blockedIds ->
                            val mySessionId = currentSessionId ?: ""
                            participantsMap.values.filter { 
                                val profileId = it.id.split(":").first()
                                it.id.contains(mySessionId).not() && profileId !in blockedIds
                            }
                        }
                        .flowOn(Dispatchers.Default)
                        .collect { participants ->
                            println("DEBUG: FocusRoomViewModel - Updating UI with ${participants.size} other participants")
                            _uiState.update { state ->
                                state.copy(otherParticipants = participants.map { p ->
                                    val profileId = p.id.split(":").first()
                                    RoomParticipant(
                                        id = profileId,
                                        name = p.name,
                                        activeTaskText = p.activeTaskText,
                                        completedTasks = p.completedTasks,
                                        totalTasks = p.totalTasks,
                                    )
                                })
                            }
                        }
                }

                // 5. INITIATE SUBSCRIPTION (Parallel)
                launch {
                    delay(200.milliseconds) // Tiny delay to let the listener definitely start
                    println("DEBUG: FocusRoomViewModel - [4/4] Triggering WebSocket subscription")
                    roomRepository.subscribe()
                    
                    // Initial sync after join
                    delay(500)
                    syncWithOthers()
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

            // Use a session-unique ID to prevent collisions on the Supabase server
            // between old 'leave' events and new 'join' events during rapid re-entry.
            val uniquePresenceId = "${profile.id}:${currentSessionId}"

            val presence = ParticipantPresence(
                id = uniquePresenceId,
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
        println("DEBUG: FocusRoomViewModel - Starting global cleanup (finishWork)...")
        val sessionId = currentSessionId ?: return
        val state = _uiState.value
        val lastTask = state.tasks.find { it.id == state.activeTaskId }?.text ?: "Focusing"

        viewModelScope.launch {
            try {
                // 1. Immediate unsubscription to free up the channel for next potential join
                roomRepository.leaveRoom()
                println("DEBUG: FocusRoomViewModel - Channel cleaned up successfully.")

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
        // ViewModel is being destroyed. We use a non-cancelled scope to ensure cleanup completes.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("DEBUG: FocusRoomViewModel - onCleared cleanup...")
                roomRepository.leaveRoom()
            } catch (e: Exception) {
                println("DEBUG: FocusRoomViewModel - leaveRoom cleanup error: ${e.message}")
            }
        }
    }
}
