package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.room.ActiveFocusSessionSnapshot
import com.grepho.cozydoubling.core.room.FocusNotificationAction
import com.grepho.cozydoubling.core.room.FocusSessionLifecycleController
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

    private val _activeSession = MutableStateFlow<ActiveFocusSessionSnapshot?>(null)
    override val activeSession: StateFlow<ActiveFocusSessionSnapshot?> = _activeSession.asStateFlow()

    private val _isPipEnabled = MutableStateFlow(initialPipEnabled)
    override val isPipEnabled: StateFlow<Boolean> = _isPipEnabled.asStateFlow()

    private val _notificationActions = MutableSharedFlow<FocusNotificationAction>(extraBufferCapacity = 8)
    override val notificationActions: SharedFlow<FocusNotificationAction> = _notificationActions.asSharedFlow()

    override fun startOrResumeSession(snapshot: ActiveFocusSessionSnapshot) {
        persistedSession = snapshot.sessionId to snapshot.startTimeEpochMs
        _activeSession.value = snapshot
    }

    override fun updateSession(snapshot: ActiveFocusSessionSnapshot) {
        if (_activeSession.value != null) {
            persistedSession = snapshot.sessionId to snapshot.startTimeEpochMs
            _activeSession.value = snapshot
        }
    }

    override fun stopSession() {
        persistedSession = null
        _activeSession.value = null
    }

    override fun loadPersistedSession(): Pair<String, Long>? = persistedSession

    override fun setPipEnabled(enabled: Boolean) {
        _isPipEnabled.value = enabled
    }

    override fun emitNotificationAction(action: FocusNotificationAction) {
        _notificationActions.tryEmit(action)
    }
}
