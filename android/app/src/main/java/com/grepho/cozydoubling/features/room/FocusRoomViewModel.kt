package com.grepho.cozydoubling.features.room

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FocusRoomViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val roomRepository: FocusRoomRepository,
    private val profileRepository: ProfileRepository,
    private val safetyRepository: SafetyRepository,
    private val authRepository: com.grepho.cozydoubling.core.auth.AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    constructor(savedStateHandle: SavedStateHandle) : this(
        savedStateHandle = savedStateHandle,
        roomRepository = ServiceLocator.focusRoomRepository,
        profileRepository = ServiceLocator.profileRepository,
        safetyRepository = ServiceLocator.safetyRepository,
        authRepository = ServiceLocator.authRepository
    )

    val profile: StateFlow<com.grepho.cozydoubling.core.profile.Profile?> = profileRepository.profile

    private val _uiState = MutableStateFlow(restoreUiState())
    val uiState: StateFlow<FocusRoomUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = savedStateHandle["session_id"]

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
                
                // 1. Start session in DB or RESTORE existing one
                val sessionId = if (currentSessionId == null) {
                    val newId = try {
                        roomRepository.startSession()
                    } catch (e: Exception) {
                        println("DEBUG: FocusRoomViewModel - startSession exception: ${e.message}")
                        null
                    } ?: java.util.UUID.randomUUID().toString()
                    println("DEBUG: FocusRoomViewModel - Using Session ID: $newId")
                    currentSessionId = newId
                    savedStateHandle["session_id"] = newId
                    newId
                } else {
                    println("DEBUG: FocusRoomViewModel - RESTORED Session ID: $currentSessionId")
                    currentSessionId!!
                }

                // 2. Prepare flows (DO NOT subscribe yet)
                val currentProfile = profileRepository.profile.value 
                    ?: withTimeoutOrNull(2000) { profileRepository.profile.filterNotNull().first() }
                val myUserId = currentProfile?.id ?: authRepository.getCurrentUserId() ?: "user_default"
                val uniquePresenceId = "$myUserId:$sessionId"
                val presenceFlow = roomRepository.joinRoom(uniquePresenceId)

                // 3. START LISTENING (Parallel coroutine)
                // This ensures we are listening BEFORE the subscription starts.
                launch {
                    println("DEBUG: FocusRoomViewModel - [3/4] Listener coroutine active. My ID: $uniquePresenceId")
                    presenceFlow
                        .onEach { list -> println("DEBUG: FocusRoomViewModel - RECEIVED PRESENCE: ${list.size} users") }
                        .combine(safetyRepository.blockedUserIds) { participants, blockedIds ->
                            participants.filter { participant ->
                                val participantProfileId = participant.id.split(":").first()
                                // Exclude own profile and blocked users
                                participantProfileId != myUserId && participantProfileId !in blockedIds
                            }
                        }
                        .flowOn(Dispatchers.Default)
                        .collect { participants ->
                            println("DEBUG: FocusRoomViewModel - Updating UI with ${participants.size} other participants")
                            val uniqueParticipants = participants
                                .groupBy { it.id.split(":").first() }
                                .map { (pId, presenceList) ->
                                    val p = presenceList.last()
                                    RoomParticipant(
                                        id = pId,
                                        name = p.name,
                                        activeTaskText = p.displayTaskText,
                                        completedTasks = p.completedTasks,
                                        totalTasks = p.totalTasks,
                                    )
                                }
                            _uiState.update { state ->
                                state.copy(otherParticipants = uniqueParticipants)
                            }
                        }
                }

                // 5. AUTO-SYNC ON RECONNECT
                // This ensures presence is re-announced whenever the socket connects or reconnects.
                launch {
                    roomRepository.getChannelStatus()
                        .filter { it == RoomConnectionStatus.CONNECTED }
                        .collect {
                            println("DEBUG: FocusRoomViewModel - Channel CONNECTED, triggering sync")
                            syncWithOthers()
                        }
                }

                // 6. INITIATE SUBSCRIPTION (Parallel)
                launch {
                    delay(200.milliseconds) // Tiny delay to let the listener definitely start
                    println("DEBUG: FocusRoomViewModel - [4/4] Triggering WebSocket subscription")
                    roomRepository.subscribe()
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
            val myProfile = profileRepository.profile.value
            val myId = myProfile?.id ?: authRepository.getCurrentUserId() ?: run {
                println("DEBUG: FocusRoomViewModel - syncWithOthers: No user ID found")
                return
            }
            val myName = myProfile?.displayName ?: "Cozy Doubler"
            val state = _uiState.value
            val activeTask = state.tasks.find { it.id == state.activeTaskId }

            // Use a session-unique ID to prevent collisions on the room server
            // between old 'leave' events and new 'join' events during rapid re-entry.
            val uniquePresenceId = "$myId:${currentSessionId}"

            val presence = ParticipantPresence(
                id = uniquePresenceId,
                name = myName,
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
                val now = System.currentTimeMillis()
                
                // Cancel any pending sync since we have a fresh one
                pendingPresenceJob?.cancel()

                if (now - lastPresenceSyncTime > SYNC_INTERVAL_MS) {
                    // It's been a while, sync presence immediately
                    lastPresenceSyncTime = now
                    roomRepository.updatePresence(presence)
                } else {
                    // Instant broadcast path, schedule a trailing sync
                    roomRepository.broadcastPresence(presence)
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
        savedStateHandle["active_task_id"] = taskId
        triggerSync()
    }

    fun onTaskToggleStatus(taskId: String) {
        _uiState.update { state ->
            val updatedTasks = state.tasks.map {
                if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
            }
            saveTasks(updatedTasks)
            state.copy(tasks = updatedTasks)
        }
        triggerSync()
    }

    fun onAddTask(text: String) {
        if (text.isBlank()) return
        val newTask = FocusTask(id = System.currentTimeMillis().toString(), text = text)

        _uiState.update { state ->
            val updatedTasks = state.tasks + newTask
            val newActiveId = state.activeTaskId ?: newTask.id
            saveTasks(updatedTasks)
            savedStateHandle["active_task_id"] = newActiveId
            state.copy(
                tasks = updatedTasks,
                activeTaskId = newActiveId
            )
        }
        triggerSync()
    }

    fun onDeleteTask(taskId: String) {
        _uiState.update { state ->
            val task = state.tasks.find { it.id == taskId }
            // Only allow deleting non-finished tasks
            if (task == null || task.isCompleted) return@update state
            
            val updatedTasks = state.tasks.filter { it.id != taskId }
            val newActiveId = if (state.activeTaskId == taskId) {
                // If we deleted the active task, pick the first remaining one or null
                updatedTasks.firstOrNull()?.id
            } else {
                state.activeTaskId
            }
            
            saveTasks(updatedTasks)
            savedStateHandle["active_task_id"] = newActiveId
            state.copy(
                tasks = updatedTasks,
                activeTaskId = newActiveId
            )
        }
        triggerSync()
    }

    private fun saveTasks(tasks: List<FocusTask>) {
        try {
            savedStateHandle["tasks_json"] = Json.encodeToString(tasks)
            LocalTaskDataSource.saveUnfinishedTasks(tasks)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreUiState(): FocusRoomUiState {
        val tasksJson: String? = savedStateHandle["tasks_json"]
        val activeTaskId: String? = savedStateHandle["active_task_id"]
        
        val tasks = tasksJson?.let {
            try {
                Json.decodeFromString<List<FocusTask>>(it)
            } catch (e: Exception) {
                println("DEBUG: FocusRoomViewModel - Failed to restore tasks: ${e.message}")
                emptyList()
            }
        } ?: LocalTaskDataSource.loadUnfinishedTasks()

        val restoredActiveId = activeTaskId ?: tasks.firstOrNull { !it.isCompleted }?.id

        return FocusRoomUiState(
            tasks = tasks,
            activeTaskId = restoredActiveId
        )
    }

    fun onBlockUser(userId: String) {
        viewModelScope.launch {
            safetyRepository.blockUser(userId)
        }
    }

    fun onReportUser(userId: String, reason: String) {
        viewModelScope.launch {
            safetyRepository.reportUser(userId, reason)
        }
    }

    fun finishWork(onComplete: (String) -> Unit) {
        println("DEBUG: FocusRoomViewModel - Starting global cleanup (finishWork)...")
        val sessionId = currentSessionId ?: java.util.UUID.randomUUID().toString()
        val state = _uiState.value
        val lastTask = state.tasks.find { it.id == state.activeTaskId }?.text ?: "Focusing"

        viewModelScope.launch {
            try {
                // 1. Immediate unsubscription to free up the channel for next potential join
                try {
                    roomRepository.leaveRoom()
                    println("DEBUG: FocusRoomViewModel - Channel cleaned up successfully.")
                } catch (e: Exception) {
                    println("DEBUG: FocusRoomViewModel - leaveRoom error: ${e.message}")
                }

                try {
                    roomRepository.finishSession(sessionId, state.tasks.count { it.isCompleted }, lastTask)
                    profileRepository.refreshProfile()
                } catch (e: Exception) {
                    println("DEBUG: FocusRoomViewModel - finishSession error: ${e.message}")
                }
            } finally {
                onComplete(sessionId)
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
