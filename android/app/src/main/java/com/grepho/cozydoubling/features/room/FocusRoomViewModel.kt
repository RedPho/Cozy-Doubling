package com.grepho.cozydoubling.features.room

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.room.ActiveFocusSessionSnapshot
import com.grepho.cozydoubling.core.room.FocusNotificationAction
import com.grepho.cozydoubling.core.room.FocusSessionLifecycleController
import com.grepho.cozydoubling.core.room.InterruptedFocusSession
import com.grepho.cozydoubling.core.safety.SafetyRepository
import com.grepho.cozydoubling.core.validation.TextLimits
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
    private val authRepository: com.grepho.cozydoubling.core.auth.AuthRepository = ServiceLocator.authRepository,
    private val sessionController: FocusSessionLifecycleController = ServiceLocator.focusSessionController,
    private val scopeOverride: CoroutineScope? = null
) : ViewModel() {

    constructor(savedStateHandle: SavedStateHandle) : this(
        savedStateHandle = savedStateHandle,
        roomRepository = ServiceLocator.focusRoomRepository,
        profileRepository = ServiceLocator.profileRepository,
        safetyRepository = ServiceLocator.safetyRepository,
        authRepository = ServiceLocator.authRepository,
        sessionController = ServiceLocator.focusSessionController,
        scopeOverride = null
    )

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    val profile: StateFlow<com.grepho.cozydoubling.core.profile.Profile?> = profileRepository.profile

    private val isRecoveryEntry: Boolean = savedStateHandle.get<Boolean>("recovery") ?: false
    private val savedSessionId: String? = savedStateHandle.get<String>("session_id")
    private val initialRestoredSession: Pair<String, Long>? = resolveInitialSession()
    private var wasRestoredSession = initialRestoredSession != null
    private var currentSessionId: String? = initialRestoredSession?.first
    private var sessionStartEpochMs: Long =
        savedStateHandle["session_start_ms"] ?: initialRestoredSession?.second ?: System.currentTimeMillis()
    @Volatile private var isFinishing = false
    private var startRoomJob: Job? = null
    @Volatile private var currentPresenceKey: String? = null

    private val _uiState = MutableStateFlow(restoreUiState().copy(sessionStartEpochMs = sessionStartEpochMs))
    val uiState: StateFlow<FocusRoomUiState> = _uiState.asStateFlow()

    private val _autoFinishEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val autoFinishEvents: SharedFlow<String> = _autoFinishEvents.asSharedFlow()

    // Throttling state for Presence (track)
    private var lastPresenceSyncTime = 0L
    private var pendingPresenceJob: Job? = null
    private val SYNC_INTERVAL_MS = 10_000L // 10 seconds for Presence track

    init {
        startRoom()
    }

    private fun resolveInitialSession(): Pair<String, Long>? {
        val liveSnapshot = sessionController.activeSession.value
        val persisted = sessionController.loadPersistedSession()

        if (savedSessionId != null) {
            val startMs = savedStateHandle.get<Long>("session_start_ms")
                ?: liveSnapshot?.takeIf { it.sessionId == savedSessionId }?.startTimeEpochMs
                ?: persisted?.takeIf { it.first == savedSessionId }?.second
                ?: System.currentTimeMillis()
            return savedSessionId to startMs
        }

        if (isRecoveryEntry) {
            return liveSnapshot?.let { it.sessionId to it.startTimeEpochMs } ?: persisted
        }

        // Explicit new-session entry from Home with no live session: purge any stale persisted ID
        if (liveSnapshot == null && persisted != null) {
            sessionController.stopSession()
        }
        return liveSnapshot?.let { it.sessionId to it.startTimeEpochMs }
    }

    private fun startRoom() {
        startRoomJob?.cancel()
        startRoomJob = scope.launch {
            try {
                println("DEBUG: FocusRoomViewModel - [1/4] Starting room setup sequence")

                suspend fun createFreshSession(): String {
                    val newId = try {
                        roomRepository.startSession()
                    } catch (e: Exception) {
                        println("DEBUG: FocusRoomViewModel - startSession exception: ${e.message}")
                        null
                    } ?: java.util.UUID.randomUUID().toString()
                    if (isFinishing) return newId
                    println("DEBUG: FocusRoomViewModel - Using Session ID: $newId")
                    currentSessionId = newId
                    sessionStartEpochMs = System.currentTimeMillis()
                    savedStateHandle["session_id"] = newId
                    savedStateHandle["session_start_ms"] = sessionStartEpochMs
                    _uiState.update { it.copy(sessionStartEpochMs = sessionStartEpochMs) }
                    return newId
                }

                // 1. Start session in DB or RESTORE existing one (handles config changes & process death)
                var sessionId = currentSessionId ?: createFreshSession()
                if (isFinishing) return@launch

                if (wasRestoredSession) {
                    savedStateHandle["session_id"] = sessionId
                    savedStateHandle["session_start_ms"] = sessionStartEpochMs
                    _uiState.update { it.copy(sessionStartEpochMs = sessionStartEpochMs) }

                    val existing = try {
                        roomRepository.fetchSession(sessionId)
                    } catch (_: Exception) {
                        null
                    }
                    if (isFinishing) return@launch

                    if (existing?.isProcessed == true) {
                        if (isRecoveryEntry) {
                            isFinishing = true
                            sessionController.stopSession()
                            profileRepository.refreshProfile()
                            _autoFinishEvents.tryEmit(sessionId)
                            return@launch
                        } else {
                            // Never bounce an explicit Focus Room entry into an old session's Summary
                            sessionController.stopSession()
                            savedStateHandle.remove<String>("session_id")
                            savedStateHandle.remove<Long>("session_start_ms")
                            wasRestoredSession = false
                            currentSessionId = null
                            sessionId = createFreshSession()
                            if (isFinishing) return@launch
                        }
                    }
                }

                // Start foreground service + persistent notification
                sessionController.startOrResumeSession(buildSessionSnapshot())

                // Observe server-initiated session expiration, cap, and verified time sync events
                launch {
                    roomRepository.observeSessionEvents().collect { event ->
                        when (event) {
                            is RoomSessionEvent.SessionEndedByServer -> {
                                if (!isFinishing &&
                                    event.sessionId.isNotBlank() &&
                                    event.sessionId == currentSessionId
                                ) {
                                    isFinishing = true
                                    val endedId = event.sessionId
                                    pendingPresenceJob?.cancel()
                                    sessionController.stopSession()
                                    try {
                                        roomRepository.leaveRoom(currentPresenceKey)
                                        profileRepository.refreshProfile()
                                    } catch (_: Exception) {
                                    }
                                    _autoFinishEvents.tryEmit(endedId)
                                    startRoomJob?.cancel()
                                }
                            }
                            is RoomSessionEvent.SessionTimeSynced -> {
                                if (!isFinishing &&
                                    event.sessionId.isNotBlank() &&
                                    event.sessionId == currentSessionId
                                ) {
                                    val now = System.currentTimeMillis()
                                    val localElapsedSeconds = maxOf(0L, (now - sessionStartEpochMs) / 1000L)
                                    val driftSeconds = kotlin.math.abs(localElapsedSeconds - event.activeSeconds.toLong())
                                    if (event.forceSync || driftSeconds >= 2L) {
                                        sessionStartEpochMs = now - (event.activeSeconds.toLong() * 1000L)
                                        savedStateHandle["session_start_ms"] = sessionStartEpochMs
                                        _uiState.update { it.copy(sessionStartEpochMs = sessionStartEpochMs) }
                                        publishSessionSnapshot()
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Prepare flows (DO NOT subscribe yet)
                val currentProfile = profileRepository.profile.value
                    ?: withTimeoutOrNull(2000) { profileRepository.profile.filterNotNull().first() }
                if (isFinishing) return@launch

                val myUserId = currentProfile?.id ?: authRepository.getCurrentUserId() ?: "user_default"
                val uniquePresenceId = "$myUserId:$sessionId"
                currentPresenceKey = uniquePresenceId
                val presenceFlow = roomRepository.joinRoom(uniquePresenceId)

                // 3. Collect notification actions from persistent notification
                launch {
                    sessionController.notificationActions.collect { action ->
                        if (isFinishing || action.sessionId != currentSessionId) return@collect
                        when (action) {
                            is FocusNotificationAction.CompleteActiveTask -> {
                                val state = _uiState.value
                                val target = state.tasks.find { it.id == state.activeTaskId && !it.isCompleted }
                                    ?: state.tasks.firstOrNull { !it.isCompleted }
                                if (target != null) {
                                    onTaskToggleStatus(target.id)
                                }
                            }
                            is FocusNotificationAction.FinishSession -> {
                                finishWork { finishedId ->
                                    _autoFinishEvents.tryEmit(finishedId)
                                }
                            }
                        }
                    }
                }

                // 4. START LISTENING (Parallel coroutine)
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
                            publishSessionSnapshot()
                        }
                }

                // 5. AUTO-SYNC & CONNECTION STATUS
                // Keep UI connection indicator accurate and re-announce presence on every reconnect.
                launch {
                    roomRepository.getChannelStatus()
                        .collect { status ->
                            _uiState.update { it.copy(connectionStatus = status) }
                            if (status == RoomConnectionStatus.CONNECTED) {
                                println("DEBUG: FocusRoomViewModel - Channel CONNECTED, triggering sync")
                                syncWithOthers()
                            }
                        }
                }

                // 6. INITIATE SUBSCRIPTION (Parallel)
                launch {
                    delay(200.milliseconds) // Tiny delay to let the listener definitely start
                    if (isFinishing) return@launch
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
        scope.launch {
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

    private fun buildSessionSnapshot(state: FocusRoomUiState = _uiState.value): ActiveFocusSessionSnapshot {
        val activeTaskText = state.tasks.find { it.id == state.activeTaskId }?.text ?: "Focusing in Cozy Room"
        return ActiveFocusSessionSnapshot(
            sessionId = currentSessionId ?: "",
            startTimeEpochMs = sessionStartEpochMs,
            activeTaskText = activeTaskText,
            completedTasks = state.tasks.count { it.isCompleted },
            totalTasks = state.tasks.size,
            participantCount = state.otherParticipants.size + 1
        )
    }

    private fun publishSessionSnapshot() {
        if (isFinishing || currentSessionId.isNullOrBlank()) return
        sessionController.updateSession(buildSessionSnapshot())
    }

    private fun triggerSync() {
        publishSessionSnapshot()
        scope.launch {
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
        val sanitized = TextLimits.sanitizeTaskText(text)
        if (sanitized.isBlank()) return
        val newTask = FocusTask(id = System.currentTimeMillis().toString(), text = sanitized)

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
        scope.launch {
            safetyRepository.blockUser(userId)
        }
    }

    fun onReportUser(userId: String, reason: String) {
        scope.launch {
            safetyRepository.reportUser(userId, reason)
        }
    }

    fun finishWork(onComplete: (String) -> Unit) {
        if (isFinishing) return
        isFinishing = true
        startRoomJob?.cancel()
        pendingPresenceJob?.cancel()
        println("DEBUG: FocusRoomViewModel - Starting global cleanup (finishWork)...")
        val sessionId = currentSessionId ?: java.util.UUID.randomUUID().toString()
        val keyToLeave = currentPresenceKey
        val state = _uiState.value
        val lastTask = state.tasks.find { it.id == state.activeTaskId }?.text ?: "Focusing"

        sessionController.stopSession()

        scope.launch {
            try {
                // 1. Immediate unsubscription to free up the channel for next potential join
                try {
                    roomRepository.leaveRoom(keyToLeave)
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
        startRoomJob?.cancel()
        pendingPresenceJob?.cancel()
        if (!isFinishing) {
            val sessionId = currentSessionId
            val activeId = sessionController.activeSession.value?.sessionId
            if (sessionId != null && (activeId == null || activeId == sessionId)) {
                val state = _uiState.value
                val lastTask = state.tasks.find { it.id == state.activeTaskId }?.text ?: "Focusing"
                sessionController.markSessionInterrupted(
                    InterruptedFocusSession(
                        sessionId = sessionId,
                        startTimeEpochMs = sessionStartEpochMs,
                        tasksCompleted = state.tasks.count { it.isCompleted },
                        lastTaskText = lastTask
                    )
                )
            }
            val keyToLeave = currentPresenceKey
            if (keyToLeave != null) {
                // ViewModel is being destroyed without finishWork(); ensure room presence closes cleanly.
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        println("DEBUG: FocusRoomViewModel - onCleared cleanup...")
                        roomRepository.leaveRoom(keyToLeave)
                    } catch (e: Exception) {
                        println("DEBUG: FocusRoomViewModel - leaveRoom cleanup error: ${e.message}")
                    }
                }
            }
        }
    }
}
