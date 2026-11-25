package com.grepho.cozydoubling.core.room

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class ActiveFocusSessionSnapshot(
    val sessionId: String,
    val startTimeEpochMs: Long,
    val activeTaskText: String,
    val completedTasks: Int,
    val totalTasks: Int,
    val participantCount: Int
)

data class InterruptedFocusSession(
    val sessionId: String,
    val startTimeEpochMs: Long,
    val tasksCompleted: Int = 0,
    val lastTaskText: String = ""
)

sealed interface FocusNotificationAction {
    val sessionId: String
    data class CompleteActiveTask(override val sessionId: String) : FocusNotificationAction
    data class FinishSession(override val sessionId: String) : FocusNotificationAction
}

object FocusSessionIntentExtras {
    const val EXTRA_RETURN_TO_FOCUS_ROOM = "return_to_focus_room"
    const val EXTRA_FINISH_SESSION = "finish_focus_session"
    const val EXTRA_SESSION_ID = "focus_session_id"
}

/**
 * Returns true when leaving PiP back to an active fullscreen foreground window while
 * a Focus session is still active, so the user stays in Focus Room instead of Home.
 */
fun shouldRestoreFocusRoomOnPipExit(
    wasInPip: Boolean,
    isNowInPip: Boolean,
    isActivityStarted: Boolean,
    hasActiveSession: Boolean
): Boolean = wasInPip && !isNowInPip && isActivityStarted && hasActiveSession

/**
 * Clean Architecture boundary isolating Android foreground-service / PiP / session-recovery
 * lifecycle from FocusRoomViewModel and SettingsViewModel.
 */
interface FocusSessionLifecycleController {
    val activeSession: StateFlow<ActiveFocusSessionSnapshot?>
    val isPipEnabled: StateFlow<Boolean>
    val notificationActions: SharedFlow<FocusNotificationAction>

    fun startOrResumeSession(snapshot: ActiveFocusSessionSnapshot)
    fun updateSession(snapshot: ActiveFocusSessionSnapshot)
    fun stopSession()
    fun loadPersistedSession(): Pair<String, Long>?
    fun markSessionInterrupted(session: InterruptedFocusSession)
    fun loadInterruptedSession(): InterruptedFocusSession?
    fun clearInterruptedSession()
    fun setPipEnabled(enabled: Boolean)
    fun emitNotificationAction(action: FocusNotificationAction)
}
