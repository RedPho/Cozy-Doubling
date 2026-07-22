package com.grepho.cozydoubling.features.room

import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.network.ConnectionStateManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class FocusRoomRepository(private val scope: CoroutineScope) {

    private var roomChannel: RealtimeChannel? = null
    private var lastPresenceUpdateTime = 0L
    private var pendingPresenceJob: Job? = null

    // Track the latest presence state locally so we can re-sync it on reconnection
    private val currentPresence = MutableStateFlow<ParticipantPresence?>(null)

    // Raw participant map — keyed by userId
    private val _otherParticipantsMap = MutableStateFlow<Map<String, RoomParticipant>>(emptyMap())

    // Filtered UI list (blocked users removed)
    val otherParticipants: StateFlow<List<RoomParticipant>> = _otherParticipantsMap
        .map { it.values.toList() }
        .combine(SafetyRepository.blockedUserIds) { list, blocked ->
            list.filter { it.id !in blocked }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun joinRoom(initialPresence: ParticipantPresence) {
        currentPresence.value = initialPresence
        try {
            val myId = Supabase.client.auth.currentUserOrNull()?.id ?: return

            // Clean up any old channel before creating a new one
            Supabase.client.realtime.subscriptions["realtime:room:cozy"]?.let {
                Supabase.client.realtime.removeChannel(it)
            }
            _otherParticipantsMap.value = emptyMap()

            val channel = Supabase.client.realtime.channel("room:cozy") {
                presence { key = myId }
            }
            roomChannel = channel

            // A. Presence: full snapshot via presenceDataFlow.
            //    This is the authoritative list of WHO is in the room.
            //    We use a map so later broadcasts can update task state without
            //    being blown away by the next presence heartbeat.
            scope.launch {
                try {
                    channel.presenceDataFlow<ParticipantPresence>()
                        .collect { all ->
                            _otherParticipantsMap.update { current ->
                                val incoming = all
                                    .filter { it.id != myId }
                                    .associateBy { it.id }
                                // Merge: keep task state from broadcast if newer
                                incoming.mapValues { (id, presence) ->
                                    val existing = current[id]
                                    RoomParticipant(
                                        id = presence.id,
                                        name = presence.name,
                                        // Prefer existing task state (may be newer from broadcast),
                                        // fall back to what presence carries
                                        activeTaskText = existing?.activeTaskText ?: presence.activeTaskText,
                                        completedTasks = existing?.completedTasks ?: presence.completedTasks,
                                        totalTasks = existing?.totalTasks ?: presence.totalTasks
                                    )
                                }
                            }
                        }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                    ConnectionStateManager.reportServerError()
                }
            }

            // B. Broadcast: low-latency task state updates between presence heartbeats.
            //    Only updates task fields; never adds/removes participants (that's presence's job).
            scope.launch {
                try {
                    channel.broadcastFlow<ParticipantAction>("action").collect { action ->
                        if (action.id == myId) return@collect
                        _otherParticipantsMap.update { current ->
                            val existing = current[action.id] ?: return@update current
                            current + (action.id to existing.copy(
                                activeTaskText = action.activeTaskText,
                                completedTasks = action.completedTasks,
                                totalTasks = action.totalTasks
                            ))
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            }

            channel.subscribe()

            // C. Re-track presence every time the channel (re)connects.
            //    Must be launched AFTER subscribe() so the channel status StateFlow
            //    already holds the correct value when we start collecting.
            scope.launch {
                try {
                    channel.status.collect { status ->
                        if (status == RealtimeChannel.Status.SUBSCRIBED) {
                            currentPresence.value?.let { syncPresence(it) }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                }
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
        }
    }

    fun broadcastUpdate(action: ParticipantAction) {
        val channel = roomChannel ?: return
        scope.launch {
            try {
                // Instant broadcast — supabase-kt falls back to REST if not yet SUBSCRIBED
                channel.broadcast("action", action)

                // Throttled presence track (at most once per 10 s, with a trailing 7 s update)
                pendingPresenceJob?.cancel()
                val now = System.currentTimeMillis()
                val presence = ParticipantPresence(
                    id = action.id,
                    name = ProfileRepository.profile.value?.displayName ?: "User",
                    activeTaskText = action.activeTaskText,
                    completedTasks = action.completedTasks,
                    totalTasks = action.totalTasks
                )
                currentPresence.value = presence

                if (now - lastPresenceUpdateTime > 10_000L) {
                    lastPresenceUpdateTime = now
                    syncPresence(presence)
                }
                pendingPresenceJob = launch {
                    delay(7_000L)
                    lastPresenceUpdateTime = System.currentTimeMillis()
                    syncPresence(presence)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
            }
        }
    }

    private suspend fun syncPresence(presence: ParticipantPresence) {
        try {
            val channel = roomChannel ?: return
            // track() throws if channel is not SUBSCRIBED — guard required
            if (channel.status.value == RealtimeChannel.Status.SUBSCRIBED) {
                channel.track(presence)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }

    suspend fun leaveRoom() {
        try {
            pendingPresenceJob?.cancel()
            roomChannel?.let {
                it.unsubscribe()
                Supabase.client.realtime.removeChannel(it)
            }
            roomChannel = null
            _otherParticipantsMap.value = emptyMap()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        }
    }

    // ── Session database logic ───────────────────────────────────────────────

    suspend fun startSession(): String? {
        val user = Supabase.client.auth.currentUserOrNull() ?: return null
        return try {
            val session = Supabase.client.postgrest["focus_sessions"]
                .insert(mapOf("user_id" to user.id)) { select() }
                .decodeSingle<FocusSession>()
            session.id
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
            null
        }
    }

    suspend fun finishSession(sessionId: String, tasksCompleted: Int, taskText: String) {
        try {
            Supabase.client.postgrest.rpc(
                function = "finish_session",
                parameters = FinishSessionParams(sessionId, tasksCompleted, taskText)
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
