package com.grepho.cozydoubling.features.room

import com.grepho.cozydoubling.core.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import com.grepho.cozydoubling.core.profile.ProfileRepository
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*



class FocusRoomRepository(private val scope: CoroutineScope) {

    private var roomChannel: RealtimeChannel? = null
    private var lastPresenceUpdateTime = 0L
    private var pendingPresenceJob: Job? = null

    // 1. A unified flow of other people in the room
    private val _otherParticipants = MutableStateFlow<List<RoomParticipant>>(emptyList())
    val otherParticipants: StateFlow<List<RoomParticipant>> = _otherParticipants.asStateFlow()

    suspend fun joinRoom() {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: return

        // Clean up any old channel
        Supabase.client.realtime.subscriptions["room:cozy"]?.let {
            Supabase.client.realtime.removeChannel(it)
        }

        val channel = Supabase.client.realtime.channel("room:cozy") {
            presence { key = myId }
        }
        roomChannel = channel

        // Listen for the "Bulletin Board" (Presence)
        scope.launch {
            channel.presenceDataFlow<ParticipantPresence>()
                .map { all -> all.filter { it.id != myId } }
                .collect { filtered ->
                    _otherParticipants.update { current ->
                        filtered.map { p ->
                            RoomParticipant(p.id, p.name, p.activeTaskText, p.completedTasks, p.totalTasks)
                        }
                    }
                }
        }

        // Listen for the "Live Radio" (Broadcast)
        scope.launch {
            channel.broadcastFlow<ParticipantAction>("action").collect { action ->
                _otherParticipants.update { current ->
                    current.map { if (it.id == action.id) it.copy(activeTaskText = action.activeTaskText, completedTasks = action.completedTasks, totalTasks = action.totalTasks) else it }
                }
            }
        }

        channel.subscribe()

        // Initial "Hello"
        scope.launch {
            ProfileRepository.profile.filterNotNull().first()
            channel.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }.first()
            syncPresence()
        }
    }

    fun broadcastUpdate(action: ParticipantAction) {
        val channel = roomChannel ?: return
        scope.launch {
            // Instant Broadcast
            channel.broadcast<ParticipantAction>("action", action)

            // Throttled Presence
            pendingPresenceJob?.cancel()
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPresenceUpdateTime > 10000) {
                lastPresenceUpdateTime = currentTime
                syncPresence()
            }
            pendingPresenceJob = launch {
                delay(7000)
                lastPresenceUpdateTime = System.currentTimeMillis()
                syncPresence()
            }
        }
    }

    private suspend fun syncPresence() {
        val profile = ProfileRepository.profile.value ?: return
        val channel = roomChannel ?: return
        // Map the action to the presence object
        val presence = ParticipantPresence(profile.id, profile.displayName, "Focusing...", 0, 0) // Simplify for now
        channel.track(presence)
    }

    suspend fun leaveRoom() {
        roomChannel?.let {
            it.unsubscribe()
            Supabase.client.realtime.removeChannel(it)
        }
        roomChannel = null
    }

    suspend fun startSession(): String? {
        val user = Supabase.client.auth.currentUserOrNull() ?: return null

        // This inserts a row into the 'focus_sessions' table we created in SQL
        val session = Supabase.client.postgrest["focus_sessions"]
            .insert(mapOf("user_id" to user.id)) {
                select()
            }
            .decodeSingle<FocusSession>()

        return session.id
    }

    suspend fun finishSession(sessionId: String, tasksCompleted: Int, taskText: String) {
        // This calls the secure 'finish_session' RPC function on the server
        Supabase.client.postgrest.rpc(
            function = "finish_session",
            parameters = FinishSessionParams(
                sessionId = sessionId,
                tasksDone = tasksCompleted,
                taskText = taskText
            )
        )
    }

    suspend fun fetchSession(sessionId: String): FocusSession {
        return Supabase.client.postgrest["focus_sessions"]
            .select {
                filter {
                    eq("id", sessionId)
                }
            }
            .decodeSingle<FocusSession>()
    }
}