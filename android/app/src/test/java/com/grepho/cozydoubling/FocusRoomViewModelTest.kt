package com.grepho.cozydoubling

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.grepho.cozydoubling.core.room.ActiveFocusSessionSnapshot
import com.grepho.cozydoubling.core.room.FocusNotificationAction
import com.grepho.cozydoubling.data.fake.FakeAuthRepository
import com.grepho.cozydoubling.data.fake.FakeProfileRepository
import com.grepho.cozydoubling.data.fake.FakeSafetyRepository
import com.grepho.cozydoubling.data.fake.NoOpFocusSessionLifecycleController
import com.grepho.cozydoubling.features.room.FocusRoomRepository
import com.grepho.cozydoubling.features.room.FocusRoomViewModel
import com.grepho.cozydoubling.features.room.FocusSession
import com.grepho.cozydoubling.features.room.ParticipantPresence
import com.grepho.cozydoubling.features.room.RoomConnectionStatus
import com.grepho.cozydoubling.features.room.RoomSessionEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRoomViewModelTest {

    private class FakeControlledFocusRoomRepository : FocusRoomRepository {
        val startedSessions = mutableListOf<String>()
        val finishedSessions = mutableListOf<String>()
        val leaveRoomCalls = mutableListOf<String?>()
        val fetchedSessionIds = mutableListOf<String>()
        var subscribeCount = 0

        var startSessionHandler: suspend () -> String? = { "new_session_1" }
        var sessionsById = mutableMapOf<String, FocusSession>()

        private val _participants = MutableStateFlow<List<ParticipantPresence>>(emptyList())
        private val _sessionEvents = MutableSharedFlow<RoomSessionEvent>(extraBufferCapacity = 8)
        val channelStatusFlow = MutableStateFlow(RoomConnectionStatus.DISCONNECTED)

        fun emitSessionEvent(event: RoomSessionEvent) {
            _sessionEvents.tryEmit(event)
        }

        override fun joinRoom(presenceKey: String): Flow<List<ParticipantPresence>> = _participants.asStateFlow()

        override suspend fun subscribe() {
            subscribeCount += 1
        }

        override fun getChannelStatus(): Flow<RoomConnectionStatus> = channelStatusFlow.asStateFlow()

        override suspend fun broadcastPresence(presence: ParticipantPresence) {}

        override fun listenForBroadcasts(): Flow<ParticipantPresence> = MutableSharedFlow()

        override suspend fun updatePresence(presence: ParticipantPresence) {}

        override suspend fun leaveRoom(presenceKey: String?) {
            leaveRoomCalls.add(presenceKey)
        }

        override suspend fun startSession(): String? {
            val id = startSessionHandler()
            if (id != null) startedSessions.add(id)
            return id
        }

        override suspend fun finishSession(
            sessionId: String,
            tasksCompleted: Int,
            taskText: String,
            endReason: String?
        ) {
            finishedSessions.add(sessionId)
        }

        override suspend fun fetchSession(sessionId: String): FocusSession? {
            fetchedSessionIds.add(sessionId)
            return sessionsById[sessionId]
        }

        override fun observeSessionEvents(): Flow<RoomSessionEvent> = _sessionEvents.asSharedFlow()
    }

    private fun createDeps(): Triple<FakeControlledFocusRoomRepository, FakeProfileRepository, NoOpFocusSessionLifecycleController> {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(authRepo)
        val roomRepo = FakeControlledFocusRoomRepository()
        val controller = NoOpFocusSessionLifecycleController()
        return Triple(roomRepo, profileRepo, controller)
    }

    @Test
    fun explicitStartWithStaleProcessedPersistedSessionStartsFreshAndNeverOpensSummary() = runBlocking {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(authRepo)
        val roomRepo = FakeControlledFocusRoomRepository().apply {
            sessionsById["stale_old_session"] = FocusSession(
                id = "stale_old_session",
                userId = "user_cozy_001",
                startTime = "2026-11-24T10:00:00Z",
                endTime = "2026-11-24T10:25:00Z",
                isProcessed = true,
                durationMinutes = 25
            )
            startSessionHandler = { "fresh_session_123" }
        }
        val controller = NoOpFocusSessionLifecycleController(
            initialPersistedSession = "stale_old_session" to 1_700_000_000_000L
        )
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val autoFinishIds = mutableListOf<String>()

        try {
            val savedState = SavedStateHandle(mapOf("recovery" to false, "tasks_json" to "[]"))
            val vm = FocusRoomViewModel(
                savedStateHandle = savedState,
                roomRepository = roomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = controller,
                scopeOverride = scope
            )
            val collectorJob = scope.launch {
                vm.autoFinishEvents.collect { autoFinishIds.add(it) }
            }

            assertEquals(listOf("fresh_session_123"), roomRepo.startedSessions)
            assertTrue("Explicit start must not emit auto-finish summary", autoFinishIds.isEmpty())
            assertEquals("fresh_session_123", controller.activeSession.value?.sessionId)
            assertEquals("fresh_session_123", controller.loadPersistedSession()?.first)

            collectorJob.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun recoveryEntryWithProcessedPersistedSessionEmitsSummaryAndClearsStorage() = runBlocking {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(authRepo)
        val roomRepo = FakeControlledFocusRoomRepository().apply {
            sessionsById["expired_session_1"] = FocusSession(
                id = "expired_session_1",
                userId = "user_cozy_001",
                startTime = "2026-11-24T10:00:00Z",
                endTime = "2026-11-24T10:30:00Z",
                isProcessed = true,
                durationMinutes = 30
            )
        }
        val controller = NoOpFocusSessionLifecycleController(
            initialPersistedSession = "expired_session_1" to 1_700_000_000_000L
        )
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val autoFinishIds = mutableListOf<String>()

        try {
            val savedState = SavedStateHandle(mapOf("recovery" to true, "tasks_json" to "[]"))
            // Attach pre-subscribed flow collector after constructor using replay? Wait: autoFinishEvents has replay=0,
            // so we verify side effects or pass a slightly delayed fetchSession to attach collector before emit.
            val fetchGate = CompletableDeferred<Unit>()
            val gatedRoomRepo = object : FocusRoomRepository by roomRepo {
                override suspend fun fetchSession(sessionId: String): FocusSession? {
                    fetchGate.await()
                    return roomRepo.fetchSession(sessionId)
                }
            }
            val vm = FocusRoomViewModel(
                savedStateHandle = savedState,
                roomRepository = gatedRoomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = controller,
                scopeOverride = scope
            )
            val collectorJob = scope.launch {
                vm.autoFinishEvents.collect { autoFinishIds.add(it) }
            }
            fetchGate.complete(Unit)

            assertEquals(listOf("expired_session_1"), autoFinishIds)
            assertTrue(roomRepo.startedSessions.isEmpty())
            assertNull(controller.loadPersistedSession())
            assertNull(controller.activeSession.value)

            collectorJob.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun recoveryEntryWithUnprocessedPersistedSessionResumesWithoutStartingNewOne() = runBlocking {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(authRepo)
        val roomRepo = FakeControlledFocusRoomRepository().apply {
            sessionsById["active_session_99"] = FocusSession(
                id = "active_session_99",
                userId = "user_cozy_001",
                startTime = "2026-11-25T09:00:00Z",
                endTime = null,
                isProcessed = false
            )
        }
        val controller = NoOpFocusSessionLifecycleController(
            initialPersistedSession = "active_session_99" to 1_700_000_000_000L
        )
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val autoFinishIds = mutableListOf<String>()

        try {
            val savedState = SavedStateHandle(mapOf("recovery" to true, "tasks_json" to "[]"))
            val vm = FocusRoomViewModel(
                savedStateHandle = savedState,
                roomRepository = roomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = controller,
                scopeOverride = scope
            )
            val collectorJob = scope.launch {
                vm.autoFinishEvents.collect { autoFinishIds.add(it) }
            }

            assertEquals(listOf("active_session_99"), roomRepo.fetchedSessionIds)
            assertTrue(roomRepo.startedSessions.isEmpty())
            assertTrue(autoFinishIds.isEmpty())
            assertEquals("active_session_99", controller.activeSession.value?.sessionId)

            collectorJob.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun staleServerEndedEventIsIgnoredWhileMatchingEventFinishesNewSession() = runBlocking {
        val (roomRepo, profileRepo, controller) = createDeps()
        roomRepo.startSessionHandler = { "new_session_abc" }
        val authRepo = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val autoFinishIds = mutableListOf<String>()

        try {
            val savedState = SavedStateHandle(mapOf("recovery" to false, "tasks_json" to "[]"))
            val vm = FocusRoomViewModel(
                savedStateHandle = savedState,
                roomRepository = roomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = controller,
                scopeOverride = scope
            )
            val collectorJob = scope.launch {
                vm.autoFinishEvents.collect { autoFinishIds.add(it) }
            }

            // Stale event from an old session must be ignored
            roomRepo.emitSessionEvent(RoomSessionEvent.SessionEndedByServer("old_session_xyz", "abandoned_disconnect"))
            assertTrue(autoFinishIds.isEmpty())
            assertEquals("new_session_abc", controller.activeSession.value?.sessionId)

            // Matching event for the active session must finish cleanly
            roomRepo.emitSessionEvent(RoomSessionEvent.SessionEndedByServer("new_session_abc", "capped_max_duration"))
            assertEquals(listOf("new_session_abc"), autoFinishIds)
            assertNull(controller.activeSession.value)
            assertNull(controller.loadPersistedSession())

            collectorJob.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun staleNotificationFinishActionIsIgnoredWhileMatchingActionFinishesSession() = runBlocking {
        val (roomRepo, profileRepo, controller) = createDeps()
        roomRepo.startSessionHandler = { "current_session_77" }
        val authRepo = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val autoFinishIds = mutableListOf<String>()

        try {
            val savedState = SavedStateHandle(mapOf("recovery" to false, "tasks_json" to "[]"))
            val vm = FocusRoomViewModel(
                savedStateHandle = savedState,
                roomRepository = roomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = controller,
                scopeOverride = scope
            )
            val collectorJob = scope.launch {
                vm.autoFinishEvents.collect { autoFinishIds.add(it) }
            }

            // Stale notification action targeting another session ID must not trigger finish
            controller.emitNotificationAction(FocusNotificationAction.FinishSession("old_session_11"))
            assertTrue(autoFinishIds.isEmpty())
            assertEquals("current_session_77", controller.activeSession.value?.sessionId)

            // Action targeting active session finishes it once
            controller.emitNotificationAction(FocusNotificationAction.FinishSession("current_session_77"))
            assertEquals(listOf("current_session_77"), autoFinishIds)
            assertEquals(listOf("current_session_77"), roomRepo.finishedSessions)
            assertNull(controller.activeSession.value)

            collectorJob.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun finishWorkWhileStartSessionSuspendsNeverRePersistsOrResumesSession() = runBlocking {
        val (roomRepo, profileRepo, controller) = createDeps()
        val startGate = CompletableDeferred<String?>()
        roomRepo.startSessionHandler = { startGate.await() }
        val authRepo = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val completedIds = mutableListOf<String>()

        try {
            val savedState = SavedStateHandle(mapOf("recovery" to false, "tasks_json" to "[]"))
            val vm = FocusRoomViewModel(
                savedStateHandle = savedState,
                roomRepository = roomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = controller,
                scopeOverride = scope
            )

            // Finish while startSession() is still awaiting
            vm.finishWork { completedIds.add(it) }
            assertEquals(1, completedIds.size)

            // Even after startSession resolves late, controller must remain stopped
            startGate.complete("late_session_999")

            // Direct update guard also refuses mismatched/stopped snapshots
            controller.updateSession(
                ActiveFocusSessionSnapshot(
                    sessionId = "late_session_999",
                    startTimeEpochMs = 1_700_000_000_000L,
                    activeTaskText = "Should not persist",
                    completedTasks = 0,
                    totalTasks = 0,
                    participantCount = 1
                )
            )

            assertNull(controller.activeSession.value)
            assertNull(controller.loadPersistedSession())
            assertEquals(0, roomRepo.subscribeCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clearingViewModelWithoutFinishMarksSessionInterruptedForNextAppOpen() = runBlocking {
        val (roomRepo, profileRepo, controller) = createDeps()
        roomRepo.startSessionHandler = { "interrupted_sess_55" }
        val authRepo = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val store = ViewModelStore()

        try {
            val savedState = SavedStateHandle(mapOf("recovery" to false, "tasks_json" to "[]"))
            val vm = FocusRoomViewModel(
                savedStateHandle = savedState,
                roomRepository = roomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = controller,
                scopeOverride = scope
            )
            vm.onAddTask("Write physics notes")

            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
                }
            )[FocusRoomViewModel::class.java]

            // Simulate app task removal / Activity destruction without finishWork()
            store.clear()

            val interrupted = controller.loadInterruptedSession()
            assertNotNull(interrupted)
            assertEquals("interrupted_sess_55", interrupted?.sessionId)
            assertEquals("Write physics notes", interrupted?.lastTaskText)
            assertNull(controller.activeSession.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun sessionTimeSyncedAdjustsTimerAndSnapshotAndIgnoresStaleSessionId() = runBlocking {
        val (roomRepo, profileRepo, controller) = createDeps()
        roomRepo.startSessionHandler = { "synced_sess_42" }
        val authRepo = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        try {
            val savedState = SavedStateHandle(mapOf("recovery" to false, "tasks_json" to "[]"))
            val vm = FocusRoomViewModel(
                savedStateHandle = savedState,
                roomRepository = roomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = controller,
                scopeOverride = scope
            )

            val initialStartMs = vm.uiState.value.sessionStartEpochMs

            // Stale sync for a different session ID must be ignored
            roomRepo.emitSessionEvent(
                RoomSessionEvent.SessionTimeSynced(
                    sessionId = "old_sess_99",
                    activeSeconds = 3600,
                    forceSync = true
                )
            )
            assertEquals(initialStartMs, vm.uiState.value.sessionStartEpochMs)

            // Valid sync with server verified activeSeconds (e.g. 15m after a 20m offline gap)
            roomRepo.emitSessionEvent(
                RoomSessionEvent.SessionTimeSynced(
                    sessionId = "synced_sess_42",
                    activeSeconds = 900,
                    forceSync = true
                )
            )
            val updatedElapsedSecs = (System.currentTimeMillis() - vm.uiState.value.sessionStartEpochMs) / 1000L
            assertTrue(
                "Expected elapsed timer synced to ~900s, got $updatedElapsedSecs",
                updatedElapsedSecs in 898L..902L
            )
            assertEquals(vm.uiState.value.sessionStartEpochMs, controller.activeSession.value?.startTimeEpochMs)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun channelStatusTransitionsUpdateUiConnectionStatus() = runBlocking {
        val (roomRepo, profileRepo, controller) = createDeps()
        roomRepo.startSessionHandler = { "status_sess_1" }
        roomRepo.channelStatusFlow.value = RoomConnectionStatus.CONNECTING
        val authRepo = FakeAuthRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        try {
            val savedState = SavedStateHandle(mapOf("recovery" to false, "tasks_json" to "[]"))
            val vm = FocusRoomViewModel(
                savedStateHandle = savedState,
                roomRepository = roomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = controller,
                scopeOverride = scope
            )
            assertEquals(RoomConnectionStatus.CONNECTING, vm.uiState.value.connectionStatus)

            roomRepo.channelStatusFlow.value = RoomConnectionStatus.CONNECTED
            assertEquals(RoomConnectionStatus.CONNECTED, vm.uiState.value.connectionStatus)

            roomRepo.channelStatusFlow.value = RoomConnectionStatus.DISCONNECTED
            assertEquals(RoomConnectionStatus.DISCONNECTED, vm.uiState.value.connectionStatus)
        } finally {
            scope.cancel()
        }
    }
}
