package com.grepho.cozydoubling.core.safety

import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.profile.ProfileRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserBlockDto(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String
)

object SafetyRepository {
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 🚀 Reactive state, now decoupled from the combine operator for performance
    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    private var safetyChannel: RealtimeChannel? = null
    private var realtimeJob: Job? = null

    init {
        // 1. Manage Realtime lifecycle and session-based fetching
        repoScope.launch {
            ProfileRepository.profile
                .map { it?.id }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        println("DEBUG: SafetyRepository - Session started for: $userId")
                        refreshBlockedUsers(userId)
                        setupRealtime()
                    } else {
                        stopRealtime()
                        _blockedUserIds.value = emptySet()
                    }
                }
        }

        // 2. React to manual sync events (Profile updates)
        repoScope.launch {
            ProfileRepository.syncEvents.collect {
                ProfileRepository.profile.value?.id?.let { refreshBlockedUsers(it) }
            }
        }
    }

    private fun setupRealtime() {
        stopRealtime()
        val channel = Supabase.client.realtime.channel("safety-updates")
        safetyChannel = channel

        realtimeJob = repoScope.launch {
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "user_blocks"
            }.collect { action ->
                println("DEBUG: SafetyRepository - Block change detected, refreshing...")
                ProfileRepository.refreshProfile()
            }
        }

        repoScope.launch {
            try { channel.subscribe() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun stopRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        safetyChannel?.let {
            repoScope.launch {
                try {
                    it.unsubscribe()
                    Supabase.client.realtime.removeChannel(it)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        safetyChannel = null
    }

    private suspend fun refreshBlockedUsers(myId: String) {
        try {
            val blockedRows = Supabase.client.postgrest["user_blocks"]
                .select {
                    filter {
                        or {
                            eq("blocker_id", myId)
                            eq("blocked_id", myId)
                        }
                    }
                }
                .decodeList<UserBlockDto>()

            val allInvolvedIds = blockedRows.map { row ->
                if (row.blockerId == myId) row.blockedId else row.blockerId
            }.toSet()

            _blockedUserIds.value = allInvolvedIds
            println("DEBUG: SafetyRepository - Refreshed ${allInvolvedIds.size} blocked IDs.")
        } catch (e: Exception) {
            println("DEBUG: SafetyRepository - Refresh error: ${e.message}")
        }
    }

    suspend fun blockUser(targetId: String) {
        try {
            Supabase.client.postgrest.rpc(
                function = "block_user",
                parameters = mapOf("target_id" to targetId)
            )
            ProfileRepository.refreshProfile()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun reportUser(targetId: String, reason: String) {
        try {
            Supabase.client.postgrest.rpc(
                function = "report_user",
                parameters = mapOf("target_id" to targetId, "report_reason" to reason)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
