package com.grepho.cozydoubling.features.room

import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.network.ConnectionStateManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class FocusRoomRepository {

    private var roomChannel: RealtimeChannel? = null

    /**
     * Joins the focus room and returns a flow of all present participants.
     */
    suspend fun joinRoom(): Flow<List<ParticipantPresence>> = withContext(Dispatchers.IO) {
        println("DEBUG: FocusRoomRepository - Joining room channel 'focus-room'")
        val channel = Supabase.client.realtime.channel("focus-room")
        roomChannel = channel

        // Monitor status changes for debugging
        CoroutineScope(Dispatchers.IO).launch {
            channel.status.collect { status ->
                println("DEBUG: FocusRoomRepository - Channel status changed to: $status")
            }
        }

        try {
            channel.subscribe()
            println("DEBUG: FocusRoomRepository - Subscription initiated")
        } catch (e: Exception) {
            println("DEBUG: FocusRoomRepository - Subscription error: ${e.message}")
            e.printStackTrace()
        }
        channel.presenceDataFlow<ParticipantPresence>()
    }

    /**
     * Returns a flow of the channel's connection status.
     */
    fun getChannelStatus(): Flow<RealtimeChannel.Status> {
        return roomChannel?.status ?: flowOf(RealtimeChannel.Status.UNSUBSCRIBED)
    }

    suspend fun updatePresence(presence: ParticipantPresence) = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = 3
        
        while (attempts < maxAttempts) {
            try {
                val channel = roomChannel ?: return@withContext

                // Wait for subscription if not already subscribed
                if (channel.status.value != RealtimeChannel.Status.SUBSCRIBED) {
                    println("DEBUG: FocusRoomRepository - Waiting for SUBSCRIBED status before tracking (Attempt ${attempts + 1})...")
                    try {
                        withTimeout(5.seconds) {
                            channel.status.first { it == RealtimeChannel.Status.SUBSCRIBED }
                        }
                    } catch (e: TimeoutCancellationException) {
                        println("DEBUG: FocusRoomRepository - Timeout waiting for subscription on attempt ${attempts + 1}")
                        attempts++
                        delay(1.seconds) // Wait before retrying
                        continue
                    }
                }

                // A tiny "settle" delay often helps avoid immediate protocol clashes on some networks
                delay(1.5.seconds)

                val json = Json.encodeToJsonElement(presence).jsonObject
                println("DEBUG: FocusRoomRepository - Tracking presence for ${presence.name}")
                channel.track(json)
                return@withContext // Success

            } catch (e: Exception) {
                println("DEBUG: FocusRoomRepository - Track error on attempt ${attempts + 1}: ${e.message}")
                e.printStackTrace()
                attempts++
                delay(1.seconds)
            }
        }
        println("DEBUG: FocusRoomRepository - Failed to track presence after $maxAttempts attempts")
    }

    suspend fun leaveRoom() = withContext(Dispatchers.IO) {
        println("DEBUG: FocusRoomRepository - Leaving room")
        roomChannel?.let {
            try {
                it.unsubscribe()
                Supabase.client.realtime.removeChannel(it)
                println("DEBUG: FocusRoomRepository - Unsubscribed and channel removed")
            } catch (e: Exception) {
                println("DEBUG: FocusRoomRepository - Leave room error: ${e.message}")
                e.printStackTrace()
            }
        }
        roomChannel = null
    }

    // ── Session database logic ───────────────────────────────────────────────

    suspend fun startSession(): String? {
        println("DEBUG: FocusRoomRepository - Starting session...")
        val user = Supabase.client.auth.currentUserOrNull() ?: run {
            println("DEBUG: FocusRoomRepository - startSession: No user found")
            return null
        }
        return try {
            val session = Supabase.client.postgrest["focus_sessions"]
                .insert(mapOf("user_id" to user.id)) { select() }
                .decodeSingle<FocusSession>()
            println("DEBUG: FocusRoomRepository - Session started: ${session.id}")
            session.id
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("DEBUG: FocusRoomRepository - startSession error: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
            null
        }
    }

    suspend fun finishSession(sessionId: String, tasksCompleted: Int, taskText: String) {
        println("DEBUG: FocusRoomRepository - Finishing session $sessionId ($tasksCompleted tasks)")
        try {
            Supabase.client.postgrest.rpc(
                function = "finish_session",
                parameters = FinishSessionParams(sessionId, tasksCompleted, taskText),
            )
            println("DEBUG: FocusRoomRepository - Session finished successfully")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("DEBUG: FocusRoomRepository - finishSession error: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
        }
    }

    suspend fun fetchSession(sessionId: String): FocusSession? {
        return try {
            Supabase.client.postgrest["focus_sessions"]
                .select { filter { eq("id", sessionId) } }
                .decodeSingle<FocusSession>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
            null
        }
    }
}
