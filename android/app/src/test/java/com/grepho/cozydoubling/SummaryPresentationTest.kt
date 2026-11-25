package com.grepho.cozydoubling

import com.grepho.cozydoubling.features.room.FocusRoomRepository
import com.grepho.cozydoubling.features.room.FocusSession
import com.grepho.cozydoubling.features.room.ParticipantPresence
import com.grepho.cozydoubling.features.room.RoomConnectionStatus
import com.grepho.cozydoubling.features.room.RoomSessionEvent
import com.grepho.cozydoubling.features.summary.SummaryHeaderKind
import com.grepho.cozydoubling.features.summary.SummaryNoticeKind
import com.grepho.cozydoubling.features.summary.SummaryUiState
import com.grepho.cozydoubling.features.summary.SummaryViewModel
import com.grepho.cozydoubling.features.summary.resolveSummaryPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPresentationTest {

    @Test
    fun offlineGapCompletion_neverShows12HourCapNotice() {
        val presentation = resolveSummaryPresentation(
            SummaryUiState(
                focusedMinutes = 25,
                tasksFinished = 1,
                leavesEarned = 28,
                endReason = "completed_with_offline_gap",
                wasCapped = true
            )
        )
        assertEquals(SummaryHeaderKind.NORMAL, presentation.headerKind)
        assertEquals(SummaryNoticeKind.OFFLINE_GAP, presentation.noticeKind)
        assertNotEquals(SummaryNoticeKind.CAPPED_MAX_DURATION, presentation.noticeKind)
        assertFalse(presentation.showNoticeAtTop)

        // Defensive legacy fallback: wasCapped=true with endReason="completed" also never claims 12h cap
        val legacyFallback = resolveSummaryPresentation(
            SummaryUiState(
                focusedMinutes = 20,
                endReason = "completed",
                wasCapped = true
            )
        )
        assertEquals(SummaryNoticeKind.OFFLINE_GAP, legacyFallback.noticeKind)
    }

    @Test
    fun cappedMaxDuration_shows12HourCapNoticeOnlyForExplicitCapReason() {
        val presentation = resolveSummaryPresentation(
            SummaryUiState(
                focusedMinutes = 720,
                tasksFinished = 2,
                leavesEarned = 726,
                endReason = "capped_max_duration",
                wasCapped = true
            )
        )
        assertEquals(SummaryHeaderKind.NORMAL, presentation.headerKind)
        assertEquals(SummaryNoticeKind.CAPPED_MAX_DURATION, presentation.noticeKind)
        assertFalse(presentation.showNoticeAtTop)
    }

    @Test
    fun abandonedAndAppClosedAndLoadError_resolveAccurateHeadersAndTopPlacement() {
        val abandoned = resolveSummaryPresentation(
            SummaryUiState(endReason = "abandoned_disconnect", wasCapped = true, wasInterrupted = true)
        )
        assertEquals(SummaryHeaderKind.ABANDONED, abandoned.headerKind)
        assertEquals(SummaryNoticeKind.ABANDONED_DISCONNECT, abandoned.noticeKind)
        assertTrue(abandoned.showNoticeAtTop)

        val appClosed = resolveSummaryPresentation(
            SummaryUiState(endReason = "app_closed", wasInterrupted = true)
        )
        assertEquals(SummaryHeaderKind.APP_CLOSED, appClosed.headerKind)
        assertEquals(SummaryNoticeKind.APP_CLOSED, appClosed.noticeKind)
        assertTrue(appClosed.showNoticeAtTop)

        val loadError = resolveSummaryPresentation(
            SummaryUiState(endReason = "app_closed", wasInterrupted = true, loadError = true)
        )
        assertEquals(SummaryHeaderKind.APP_CLOSED, loadError.headerKind)
        assertEquals(SummaryNoticeKind.LOAD_ERROR, loadError.noticeKind)
        assertTrue(loadError.showNoticeAtTop)

        val normal = resolveSummaryPresentation(
            SummaryUiState(focusedMinutes = 30, tasksFinished = 1, leavesEarned = 33, endReason = "completed")
        )
        assertEquals(SummaryHeaderKind.NORMAL, normal.headerKind)
        assertEquals(SummaryNoticeKind.NONE, normal.noticeKind)
    }

    @Test
    fun summaryViewModel_treatsUnprocessedSessionAsLoadErrorInsteadOfZeroSuccess() {
        val fakeRepo = object : FocusRoomRepository {
            override fun joinRoom(presenceKey: String): Flow<List<ParticipantPresence>> = flowOf(emptyList())
            override suspend fun subscribe() {}
            override fun getChannelStatus(): Flow<RoomConnectionStatus> = flowOf(RoomConnectionStatus.CONNECTED)
            override suspend fun broadcastPresence(presence: ParticipantPresence) {}
            override fun listenForBroadcasts(): Flow<ParticipantPresence> = emptyFlow()
            override suspend fun updatePresence(presence: ParticipantPresence) {}
            override suspend fun leaveRoom(presenceKey: String?) {}
            override suspend fun startSession(): String? = "unprocessed_sess"
            override suspend fun finishSession(sessionId: String, tasksCompleted: Int, taskText: String, endReason: String?) {}
            override suspend fun fetchSession(sessionId: String): FocusSession = FocusSession(
                id = sessionId,
                userId = "u1",
                startTime = "2026-11-25T10:00:00Z",
                isProcessed = false
            )
            override fun observeSessionEvents(): Flow<RoomSessionEvent> = emptyFlow()
        }

        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val vm = SummaryViewModel(repository = fakeRepo, scopeOverride = scope)
            vm.loadSessionStats("unprocessed_sess", wasInterrupted = false)
            assertFalse(vm.uiState.value.isLoading)
            assertTrue(vm.uiState.value.loadError)
        } finally {
            scope.cancel()
        }
    }
}
