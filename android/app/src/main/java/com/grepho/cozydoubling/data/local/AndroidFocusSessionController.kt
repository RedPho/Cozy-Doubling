package com.grepho.cozydoubling.data.local

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
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
 * Android implementation of [FocusSessionLifecycleController] managing persisted active
 * session state, Picture-in-Picture preference, and [FocusSessionService].
 */
class AndroidFocusSessionController(context: Context) : FocusSessionLifecycleController {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeSession = MutableStateFlow<ActiveFocusSessionSnapshot?>(null)
    override val activeSession: StateFlow<ActiveFocusSessionSnapshot?> = _activeSession.asStateFlow()

    private val _isPipEnabled = MutableStateFlow(prefs.getBoolean(KEY_PIP_ENABLED, true))
    override val isPipEnabled: StateFlow<Boolean> = _isPipEnabled.asStateFlow()

    private val _notificationActions = MutableSharedFlow<FocusNotificationAction>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val notificationActions: SharedFlow<FocusNotificationAction> = _notificationActions.asSharedFlow()

    override fun startOrResumeSession(snapshot: ActiveFocusSessionSnapshot) {
        clearInterruptedKeysOnly()
        persistSession(snapshot)
        _activeSession.value = snapshot
        try {
            val intent = Intent(appContext, FocusSessionService::class.java).apply {
                action = FocusSessionService.ACTION_START
            }
            ContextCompat.startForegroundService(appContext, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun updateSession(snapshot: ActiveFocusSessionSnapshot) {
        val current = _activeSession.value ?: return
        if (current.sessionId != snapshot.sessionId) return
        persistSession(snapshot)
        _activeSession.value = snapshot
    }

    override fun stopSession() {
        prefs.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_SESSION_START_MS)
            .remove(KEY_SESSION_TASKS_COMPLETED)
            .remove(KEY_SESSION_LAST_TASK)
            .commit()
        _activeSession.value = null
        try {
            appContext.stopService(Intent(appContext, FocusSessionService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun loadPersistedSession(): Pair<String, Long>? {
        val id = prefs.getString(KEY_SESSION_ID, null)
        val startMs = prefs.getLong(KEY_SESSION_START_MS, -1L)
        return if (!id.isNullOrBlank() && startMs > 0L) id to startMs else null
    }

    override fun markSessionInterrupted(session: InterruptedFocusSession) {
        prefs.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_SESSION_START_MS)
            .remove(KEY_SESSION_TASKS_COMPLETED)
            .remove(KEY_SESSION_LAST_TASK)
            .putString(KEY_INTERRUPTED_SESSION_ID, session.sessionId)
            .putLong(KEY_INTERRUPTED_START_MS, session.startTimeEpochMs)
            .putInt(KEY_INTERRUPTED_TASKS_COMPLETED, session.tasksCompleted)
            .putString(KEY_INTERRUPTED_LAST_TASK, session.lastTaskText)
            .commit()
        _activeSession.value = null
        try {
            appContext.stopService(Intent(appContext, FocusSessionService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun loadInterruptedSession(): InterruptedFocusSession? {
        val interruptedId = prefs.getString(KEY_INTERRUPTED_SESSION_ID, null)
        val interruptedStart = prefs.getLong(KEY_INTERRUPTED_START_MS, -1L)
        if (!interruptedId.isNullOrBlank() && interruptedStart > 0L) {
            return InterruptedFocusSession(
                sessionId = interruptedId,
                startTimeEpochMs = interruptedStart,
                tasksCompleted = prefs.getInt(KEY_INTERRUPTED_TASKS_COMPLETED, 0),
                lastTaskText = prefs.getString(KEY_INTERRUPTED_LAST_TASK, "") ?: ""
            )
        }
        // Also catch abrupt process death / force-stop while an active session was persisted on disk
        if (_activeSession.value == null) {
            val persistedId = prefs.getString(KEY_SESSION_ID, null)
            val persistedStart = prefs.getLong(KEY_SESSION_START_MS, -1L)
            if (!persistedId.isNullOrBlank() && persistedStart > 0L) {
                return InterruptedFocusSession(
                    sessionId = persistedId,
                    startTimeEpochMs = persistedStart,
                    tasksCompleted = prefs.getInt(KEY_SESSION_TASKS_COMPLETED, 0),
                    lastTaskText = prefs.getString(KEY_SESSION_LAST_TASK, "") ?: ""
                )
            }
        }
        return null
    }

    override fun clearInterruptedSession() {
        val editor = prefs.edit()
            .remove(KEY_INTERRUPTED_SESSION_ID)
            .remove(KEY_INTERRUPTED_START_MS)
            .remove(KEY_INTERRUPTED_TASKS_COMPLETED)
            .remove(KEY_INTERRUPTED_LAST_TASK)
        if (_activeSession.value == null) {
            editor
                .remove(KEY_SESSION_ID)
                .remove(KEY_SESSION_START_MS)
                .remove(KEY_SESSION_TASKS_COMPLETED)
                .remove(KEY_SESSION_LAST_TASK)
        }
        editor.commit()
    }

    override fun setPipEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PIP_ENABLED, enabled).apply()
        _isPipEnabled.value = enabled
    }

    override fun emitNotificationAction(action: FocusNotificationAction) {
        val activeId = _activeSession.value?.sessionId
        if (!activeId.isNullOrBlank() && action.sessionId == activeId) {
            _notificationActions.tryEmit(action)
        }
    }

    private fun clearInterruptedKeysOnly() {
        prefs.edit()
            .remove(KEY_INTERRUPTED_SESSION_ID)
            .remove(KEY_INTERRUPTED_START_MS)
            .remove(KEY_INTERRUPTED_TASKS_COMPLETED)
            .remove(KEY_INTERRUPTED_LAST_TASK)
            .commit()
    }

    private fun persistSession(snapshot: ActiveFocusSessionSnapshot) {
        prefs.edit()
            .putString(KEY_SESSION_ID, snapshot.sessionId)
            .putLong(KEY_SESSION_START_MS, snapshot.startTimeEpochMs)
            .putInt(KEY_SESSION_TASKS_COMPLETED, snapshot.completedTasks)
            .putString(KEY_SESSION_LAST_TASK, snapshot.activeTaskText)
            .commit()
    }

    companion object {
        private const val PREFS_NAME = "cozy_doubling_focus_session"
        private const val KEY_SESSION_ID = "active_session_id"
        private const val KEY_SESSION_START_MS = "active_session_start_ms"
        private const val KEY_SESSION_TASKS_COMPLETED = "active_session_tasks_completed"
        private const val KEY_SESSION_LAST_TASK = "active_session_last_task"
        private const val KEY_INTERRUPTED_SESSION_ID = "interrupted_session_id"
        private const val KEY_INTERRUPTED_START_MS = "interrupted_session_start_ms"
        private const val KEY_INTERRUPTED_TASKS_COMPLETED = "interrupted_session_tasks_completed"
        private const val KEY_INTERRUPTED_LAST_TASK = "interrupted_session_last_task"
        private const val KEY_PIP_ENABLED = "pip_enabled"
    }
}
