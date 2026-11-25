package com.grepho.cozydoubling

import com.grepho.cozydoubling.core.room.ActiveFocusSessionSnapshot
import com.grepho.cozydoubling.core.room.InterruptedFocusSession
import com.grepho.cozydoubling.data.fake.FakeAuthRepository
import com.grepho.cozydoubling.data.fake.FakeProfileRepository
import com.grepho.cozydoubling.data.fake.NoOpFocusSessionLifecycleController
import com.grepho.cozydoubling.features.room.FocusRoomRepository
import com.grepho.cozydoubling.features.room.FocusSession
import com.grepho.cozydoubling.features.room.InterruptedSessionRecoveryCoordinator
import com.grepho.cozydoubling.features.room.ParticipantPresence
import com.grepho.cozydoubling.features.room.RoomConnectionStatus
import com.grepho.cozydoubling.features.room.RoomSessionEvent
import com.grepho.cozydoubling.features.summary.SummaryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptedSessionRecoveryTest {

    private class RecordingFocusRoomRepository : FocusRoomRepository {
        val sessions = mutableMapOf<String, FocusSession>()
        val finishCalls = mutableListOf<Triple<String, Int, String?>>()
        var failFetch = false

        override fun joinRoom(presenceKey: String): Flow<List<ParticipantPresence>> = flowOf(emptyList())
        override suspend fun subscribe() {}
        override fun getChannelStatus(): Flow<RoomConnectionStatus> = flowOf(RoomConnectionStatus.DISCONNECTED)
        override suspend fun broadcastPresence(presence: ParticipantPresence) {}
        override fun listenForBroadcasts(): Flow<ParticipantPresence> = emptyFlow()
        override suspend fun updatePresence(presence: ParticipantPresence) {}
        override suspend fun leaveRoom(presenceKey: String?) {}
        override suspend fun startSession(): String? = "new_id"

        override suspend fun finishSession(
            sessionId: String,
            tasksCompleted: Int,
            taskText: String,
            endReason: String?
        ) {
            finishCalls.add(Triple(sessionId, tasksCompleted, endReason))
            sessions[sessionId] = FocusSession(
                id = sessionId,
                userId = "user_cozy_001",
                startTime = "2026-11-25T10:00:00Z",
                endTime = "2026-11-25T10:20:00Z",
                tasksCompleted = tasksCompleted,
                earnedLeaves = (20L + tasksCompleted * 3L),
                isProcessed = true,
                durationMinutes = 20,
                endReason = endReason ?: "completed",
                wasCapped = false
            )
        }

        override suspend fun fetchSession(sessionId: String): FocusSession? {
            if (failFetch) throw RuntimeException("Network unavailable")
            return sessions[sessionId]
        }

        override fun observeSessionEvents(): Flow<RoomSessionEvent> = emptyFlow()
    }

    @Test
    fun recoverIfNeeded_finalizesUnprocessedInterruptedSessionWithAppClosedReason() = runBlocking {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(authRepo)
        val roomRepo = RecordingFocusRoomRepository().apply {
            sessions["closed_sess_1"] = FocusSession(
                id = "closed_sess_1",
                userId = "user_cozy_001",
                startTime = "2026-11-25T10:00:00Z",
                isProcessed = false
            )
        }
        val controller = NoOpFocusSessionLifecycleController().apply {
            markSessionInterrupted(
                InterruptedFocusSession(
                    sessionId = "closed_sess_1",
                    startTimeEpochMs = 1_700_000_000_000L,
                    tasksCompleted = 2,
                    lastTaskText = "Draft design doc"
                )
            )
        }

        val recovered = InterruptedSessionRecoveryCoordinator(
            sessionController = controller,
            roomRepository = roomRepo,
            profileRepository = profileRepo
        ).recoverIfNeeded()

        assertNotNull(recovered)
        assertEquals("closed_sess_1", recovered?.sessionId)
        assertEquals("app_closed", recovered?.endReason)
        assertEquals(listOf(Triple("closed_sess_1", 2, "app_closed")), roomRepo.finishCalls)
        assertNull(controller.loadInterruptedSession())
    }

    @Test
    fun recoverIfNeeded_doesNotReFinishAlreadyProcessedSessionAndPreservesServerReason() = runBlocking {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(authRepo)
        val roomRepo = RecordingFocusRoomRepository().apply {
            sessions["abandoned_sess_1"] = FocusSession(
                id = "abandoned_sess_1",
                userId = "user_cozy_001",
                startTime = "2026-11-25T08:00:00Z",
                endTime = "2026-11-25T08:25:00Z",
                isProcessed = true,
                durationMinutes = 25,
                earnedLeaves = 25,
                endReason = "abandoned_disconnect",
                wasCapped = true
            )
        }
        val controller = NoOpFocusSessionLifecycleController().apply {
            markSessionInterrupted(
                InterruptedFocusSession(
                    sessionId = "abandoned_sess_1",
                    startTimeEpochMs = 1_700_000_000_000L
                )
            )
        }

        val recovered = InterruptedSessionRecoveryCoordinator(
            sessionController = controller,
            roomRepository = roomRepo,
            profileRepository = profileRepo
        ).recoverIfNeeded()

        assertNotNull(recovered)
        assertEquals("abandoned_sess_1", recovered?.sessionId)
        assertEquals("abandoned_disconnect", recovered?.endReason)
        assertTrue("Must not re-call finishSession", roomRepo.finishCalls.isEmpty())
        assertNull(controller.loadInterruptedSession())
    }

    @Test
    fun recoverIfNeeded_returnsNullWhileActiveSessionIsLiveInMemory() = runBlocking {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(authRepo)
        val roomRepo = RecordingFocusRoomRepository()
        val controller = NoOpFocusSessionLifecycleController().apply {
            startOrResumeSession(
                ActiveFocusSessionSnapshot(
                    sessionId = "live_sess_1",
                    startTimeEpochMs = 1_700_000_000_000L,
                    activeTaskText = "Normal background session",
                    completedTasks = 0,
                    totalTasks = 1,
                    participantCount = 1
                )
            )
        }

        val recovered = InterruptedSessionRecoveryCoordinator(
            sessionController = controller,
            roomRepository = roomRepo,
            profileRepository = profileRepo
        ).recoverIfNeeded()

        assertNull(recovered)
        assertTrue(roomRepo.finishCalls.isEmpty())
        assertEquals("live_sess_1", controller.activeSession.value?.sessionId)
    }

    @Test
    fun recoverIfNeeded_detectsAbruptProcessDeathPersistedSnapshot() = runBlocking {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(authRepo)
        val roomRepo = RecordingFocusRoomRepository().apply {
            sessions["killed_sess_1"] = FocusSession(
                id = "killed_sess_1",
                userId = "user_cozy_001",
                startTime = "2026-11-25T10:00:00Z",
                isProcessed = false
            )
        }
        // Persisted session exists on disk while in-memory activeSession is null (OS kill / crash)
        val controller = NoOpFocusSessionLifecycleController(
            initialPersistedSession = "killed_sess_1" to 1_700_000_000_000L
        )

        val recovered = InterruptedSessionRecoveryCoordinator(
            sessionController = controller,
            roomRepository = roomRepo,
            profileRepository = profileRepo
        ).recoverIfNeeded()

        assertEquals("killed_sess_1", recovered?.sessionId)
        assertEquals("app_closed", recovered?.endReason)
        assertNull(controller.loadPersistedSession())
        assertNull(controller.loadInterruptedSession())
    }

    @Test
    fun summaryViewModel_mapsAppClosedAndLoadErrorStatesCleanly() = runBlocking {
        val roomRepo = RecordingFocusRoomRepository().apply {
            sessions["closed_sess_ui"] = FocusSession(
                id = "closed_sess_ui",
                userId = "user_cozy_001",
                startTime = "2026-11-25T10:00:00Z",
                endTime = "2026-11-25T10:15:00Z",
                tasksCompleted = 2,
                earnedLeaves = 21,
                isProcessed = true,
                durationMinutes = 15,
                endReason = "app_closed",
                wasCapped = false
            )
        }
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val vm = SummaryViewModel(repository = roomRepo, scopeOverride = scope)
            vm.loadSessionStats("closed_sess_ui", wasInterrupted = true)

            val loaded = vm.uiState.value
            assertFalse(loaded.isLoading)
            assertFalse(loaded.loadError)
            assertTrue(loaded.wasInterrupted)
            assertEquals("app_closed", loaded.endReason)
            assertEquals(15, loaded.focusedMinutes)
            assertEquals(2, loaded.tasksFinished)
            assertEquals(21, loaded.leavesEarned)

            // Network error fallback still surfaces helpful interrupted notice state
            roomRepo.failFetch = true
            vm.loadSessionStats("missing_or_offline", wasInterrupted = true)
            val fallback = vm.uiState.value
            assertFalse(fallback.isLoading)
            assertTrue(fallback.loadError)
            assertTrue(fallback.wasInterrupted)
            assertEquals("app_closed", fallback.endReason)
        } finally {
            scope.cancel()
        }
    }
}
