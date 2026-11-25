package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.room.ActiveFocusSessionSnapshot
import com.grepho.cozydoubling.core.room.FocusNotificationAction
import com.grepho.cozydoubling.core.room.FocusSessionLifecycleController
import com.grepho.cozydoubling.core.room.InterruptedFocusSession
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory implementation used by FakeAppContainer and JVM unit tests.
 */
class NoOpFocusSessionLifecycleController(
    initialPersistedSession: Pair<String, Long>? = null,
    initialPipEnabled: Boolean = true
) : FocusSessionLifecycleController {

    private var persistedSession: Pair<String, Long>? = initialPersistedSession
    private var persistedSnapshot: InterruptedFocusSession? = initialPersistedSession?.let {
        InterruptedFocusSession(sessionId = it.first, startTimeEpochMs = it.second)
    }
    private var interruptedSession: InterruptedFocusSession? = null

    private val _activeSession = MutableStateFlow<ActiveFocusSessionSnapshot?>(null)
    override val activeSession: StateFlow<ActiveFocusSessionSnapshot?> = _activeSession.asStateFlow()

    private val _isPipEnabled = MutableStateFlow(initialPipEnabled)
    override val isPipEnabled: StateFlow<Boolean> = _isPipEnabled.asStateFlow()

    private val _notificationActions = MutableSharedFlow<FocusNotificationAction>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val notificationActions: SharedFlow<FocusNotificationAction> = _notificationActions.asSharedFlow()

    override fun startOrResumeSession(snapshot: ActiveFocusSessionSnapshot) {
        interruptedSession = null
        persistedSession = snapshot.sessionId to snapshot.startTimeEpochMs
        persistedSnapshot = InterruptedFocusSession(
            sessionId = snapshot.sessionId,
            startTimeEpochMs = snapshot.startTimeEpochMs,
            tasksCompleted = snapshot.completedTasks,
            lastTaskText = snapshot.activeTaskText
        )
        _activeSession.value = snapshot
    }

    override fun updateSession(snapshot: ActiveFocusSessionSnapshot) {
        val current = _activeSession.value ?: return
        if (current.sessionId != snapshot.sessionId) return
        persistedSession = snapshot.sessionId to snapshot.startTimeEpochMs
        persistedSnapshot = InterruptedFocusSession(
            sessionId = snapshot.sessionId,
            startTimeEpochMs = snapshot.startTimeEpochMs,
            tasksCompleted = snapshot.completedTasks,
            lastTaskText = snapshot.activeTaskText
        )
        _activeSession.value = snapshot
    }

    override fun stopSession() {
        persistedSession = null
        persistedSnapshot = null
        _activeSession.value = null
    }

    override fun loadPersistedSession(): Pair<String, Long>? = persistedSession

    override fun markSessionInterrupted(session: InterruptedFocusSession) {
        persistedSession = null
        persistedSnapshot = null
        interruptedSession = session
        _activeSession.value = null
    }

    override fun loadInterruptedSession(): InterruptedFocusSession? {
        return interruptedSession ?: if (_activeSession.value == null) persistedSnapshot else null
    }

    override fun clearInterruptedSession() {
        interruptedSession = null
        if (_activeSession.value == null) {
            persistedSession = null
            persistedSnapshot = null
        }
    }

    override fun setPipEnabled(enabled: Boolean) {
        _isPipEnabled.value = enabled
    }

    override fun emitNotificationAction(action: FocusNotificationAction) {
        val activeId = _activeSession.value?.sessionId
        if (!activeId.isNullOrBlank() && action.sessionId == activeId) {
            _notificationActions.tryEmit(action)
        }
    }
}
