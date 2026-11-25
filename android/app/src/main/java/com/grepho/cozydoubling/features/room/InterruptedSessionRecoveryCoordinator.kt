package com.grepho.cozydoubling.features.room

import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.room.FocusSessionLifecycleController

data class RecoveredInterruptedSession(
    val sessionId: String,
    val endReason: String
)

/**
 * Detects and finalizes a focus session that ended unexpectedly when the app was closed,
 * killed, or swiped away, returning its ID so the UI can show a clear summary on re-entry.
 */
class InterruptedSessionRecoveryCoordinator(
    private val sessionController: FocusSessionLifecycleController,
    private val roomRepository: FocusRoomRepository,
    private val profileRepository: ProfileRepository
) {
    suspend fun recoverIfNeeded(): RecoveredInterruptedSession? {
        if (sessionController.activeSession.value != null) {
            return null
        }
        val interrupted = sessionController.loadInterruptedSession() ?: return null

        return try {
            val existing = runCatching { roomRepository.fetchSession(interrupted.sessionId) }.getOrNull()
            val resolvedReason = if (existing?.isProcessed == true) {
                existing.endReason ?: REASON_APP_CLOSED
            } else {
                runCatching {
                    roomRepository.finishSession(
                        sessionId = interrupted.sessionId,
                        tasksCompleted = interrupted.tasksCompleted,
                        taskText = interrupted.lastTaskText.ifBlank { "Focusing" },
                        endReason = REASON_APP_CLOSED
                    )
                }
                val refreshed = runCatching { roomRepository.fetchSession(interrupted.sessionId) }.getOrNull()
                refreshed?.endReason ?: REASON_APP_CLOSED
            }
            runCatching { profileRepository.refreshProfile() }
            RecoveredInterruptedSession(
                sessionId = interrupted.sessionId,
                endReason = resolvedReason
            )
        } finally {
            sessionController.clearInterruptedSession()
        }
    }

    companion object {
        const val REASON_APP_CLOSED = "app_closed"
    }
}
