package com.grepho.cozydoubling.data.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.grepho.cozydoubling.R
import com.grepho.cozydoubling.core.MainActivity
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.room.ActiveFocusSessionSnapshot
import com.grepho.cozydoubling.core.room.FocusNotificationAction
import com.grepho.cozydoubling.core.room.FocusSessionIntentExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps Focus Room sessions reliable in the background with a persistent chronometer
 * notification and quick actions (Return to Room, Complete Active Task, Finish Session).
 */
class FocusSessionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var collectorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_COMPLETE_TASK -> {
                ServiceLocator.focusSessionController.emitNotificationAction(
                    FocusNotificationAction.COMPLETE_ACTIVE_TASK
                )
            }
            ACTION_STOP -> {
                stopForegroundAndSelf()
            }
            else -> {
                ensureChannel()
                val snapshot = ServiceLocator.focusSessionController.activeSession.value
                    ?: fallbackSnapshot()
                val notification = buildNotification(snapshot)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                if (ServiceLocator.focusSessionController.activeSession.value == null) {
                    stopForegroundAndSelf()
                } else {
                    observeSessionUpdates()
                }
            }
        }
        return START_STICKY
    }

    private fun observeSessionUpdates() {
        if (collectorJob != null) return
        collectorJob = serviceScope.launch {
            ServiceLocator.focusSessionController.activeSession.collect { snapshot ->
                if (snapshot == null) {
                    stopForegroundAndSelf()
                } else {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildNotification(snapshot))
                }
            }
        }
    }

    private fun stopForegroundAndSelf() {
        collectorJob?.cancel()
        collectorJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_focus_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_focus_channel_description)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(snapshot: ActiveFocusSessionSnapshot): Notification {
        val returnIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(FocusSessionIntentExtras.EXTRA_RETURN_TO_FOCUS_ROOM, true)
        }
        val returnPendingIntent = PendingIntent.getActivity(
            this,
            REQ_RETURN,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(this, FocusSessionService::class.java).apply {
            action = ACTION_COMPLETE_TASK
        }
        val completePendingIntent = PendingIntent.getService(
            this,
            REQ_COMPLETE,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use an Activity PendingIntent for Finish so Android 12+ never blocks opening Summary
        val finishIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(FocusSessionIntentExtras.EXTRA_RETURN_TO_FOCUS_ROOM, true)
            putExtra(FocusSessionIntentExtras.EXTRA_FINISH_SESSION, true)
        }
        val finishPendingIntent = PendingIntent.getActivity(
            this,
            REQ_FINISH,
            finishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = snapshot.activeTaskText.ifBlank { getString(R.string.room_cozy_room) }
        val body = getString(
            R.string.notification_focus_content,
            snapshot.participantCount,
            snapshot.completedTasks,
            snapshot.totalTasks
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.primary_sage))
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setWhen(snapshot.startTimeEpochMs)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(returnPendingIntent)
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.notification_action_complete_task),
                completePendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.notification_action_finish),
                finishPendingIntent
            )
            .build()
    }

    private fun fallbackSnapshot(): ActiveFocusSessionSnapshot = ActiveFocusSessionSnapshot(
        sessionId = "",
        startTimeEpochMs = System.currentTimeMillis(),
        activeTaskText = getString(R.string.room_cozy_room),
        completedTasks = 0,
        totalTasks = 0,
        participantCount = 1
    )

    override fun onDestroy() {
        collectorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "cozy_focus_session"
        const val NOTIFICATION_ID = 4201
        const val ACTION_START = "com.grepho.cozydoubling.action.FOCUS_START"
        const val ACTION_COMPLETE_TASK = "com.grepho.cozydoubling.action.COMPLETE_TASK"
        const val ACTION_STOP = "com.grepho.cozydoubling.action.FOCUS_STOP"
        const val EXTRA_RETURN_TO_FOCUS_ROOM = "return_to_focus_room"
        const val EXTRA_FINISH_SESSION = "finish_focus_session"

        private const val REQ_RETURN = 1001
        private const val REQ_COMPLETE = 1002
        private const val REQ_FINISH = 1003
    }
}
