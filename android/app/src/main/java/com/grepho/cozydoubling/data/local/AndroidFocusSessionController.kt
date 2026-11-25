package com.grepho.cozydoubling.data.local

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
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

    private val _notificationActions = MutableSharedFlow<FocusNotificationAction>(extraBufferCapacity = 8)
    override val notificationActions: SharedFlow<FocusNotificationAction> = _notificationActions.asSharedFlow()

    override fun startOrResumeSession(snapshot: ActiveFocusSessionSnapshot) {
        persistSession(snapshot.sessionId, snapshot.startTimeEpochMs)
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
        persistSession(snapshot.sessionId, snapshot.startTimeEpochMs)
        _activeSession.value = snapshot
    }

    override fun stopSession() {
        prefs.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_SESSION_START_MS)
            .apply()
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

    override fun setPipEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PIP_ENABLED, enabled).apply()
        _isPipEnabled.value = enabled
    }

    override fun emitNotificationAction(action: FocusNotificationAction) {
        _notificationActions.tryEmit(action)
    }

    private fun persistSession(sessionId: String, startTimeEpochMs: Long) {
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putLong(KEY_SESSION_START_MS, startTimeEpochMs)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "cozy_doubling_focus_session"
        private const val KEY_SESSION_ID = "active_session_id"
        private const val KEY_SESSION_START_MS = "active_session_start_ms"
        private const val KEY_PIP_ENABLED = "pip_enabled"
    }
}
