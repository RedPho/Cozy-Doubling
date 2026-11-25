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

enum class FocusNotificationAction {
    COMPLETE_ACTIVE_TASK,
    FINISH_SESSION
}

object FocusSessionIntentExtras {
    const val EXTRA_RETURN_TO_FOCUS_ROOM = "return_to_focus_room"
    const val EXTRA_FINISH_SESSION = "finish_focus_session"
}

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
    fun setPipEnabled(enabled: Boolean)
    fun emitNotificationAction(action: FocusNotificationAction)
}
