package com.grepho.cozydoubling.features.friends

import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

object FriendsRepository {

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. Pre-loaded Friends list (Refreshes on Profile updates OR manual Sync events)
    val friends: StateFlow<List<FriendUiState>> = ProfileRepository.syncEvents
        .onStart { emit(Unit) } // Fetch immediately on app start
        .combine(ProfileRepository.profile.filterNotNull()) { _, _ ->
            println("DEBUG: FriendsRepository - Profile sync, fetching friends...")
            fetchFriendsWithStories()
        }
        .combine(SafetyRepository.blockedUserIds) { friendsList, blockedIds ->
            friendsList.filter { it.id !in blockedIds }
        }
        .stateIn(repoScope, SharingStarted.Eagerly, emptyList())

    // 2. Pre-loaded Pending Requests
    val pendingRequests: StateFlow<List<Profile>> = ProfileRepository.syncEvents
        .onStart { emit(Unit) }
        .combine(ProfileRepository.profile.filterNotNull()) { _, _ ->
            println("DEBUG: FriendsRepository - Profile sync, fetching pending requests...")
            fetchIncomingRequests()
        }
        .combine(SafetyRepository.blockedUserIds) { requests, blockedIds ->
            requests.filter { it.id !in blockedIds }
        }
        .stateIn(repoScope, SharingStarted.Eagerly, emptyList())

    suspend fun sendFriendRequest(playerTag: String) {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: run {
            println("ERROR: sendFriendRequest - No current user ID")
            return
        }

        println("DEBUG: sendFriendRequest - Sending request to $playerTag from $myId")

        // 1. Find the target user
        val targetProfile = Supabase.client.postgrest["profiles"]
            .select { filter { eq("player_tag", playerTag.uppercase().removePrefix("#")) } }
            .decodeSingleOrNull<Profile>() ?: run {
                println("WARNING: sendFriendRequest - User not found for tag $playerTag")
                throw Exception("User not found")
            }

        if (targetProfile.id == myId) throw Exception("You can't add yourself!")

        val (id1, id2) = if (myId < targetProfile.id) myId to targetProfile.id else targetProfile.id to myId

        // 2. CHECK: Does a relationship already exist?
        val existing = Supabase.client.postgrest["friendships"]
            .select {
                filter {
                    eq("user_id_1", id1)
                    eq("user_id_2", id2)
                }
            }.decodeSingleOrNull<Friendship>()

        if (existing != null) {
            println("DEBUG: sendFriendRequest - Relationship already exists with status ${existing.status}")
            if (existing.status == "accepted") throw Exception("Already friends!")
            if (existing.requesterId == myId) throw Exception("Request already sent!")

            // If THEY sent you a request, just accept it!
            println("DEBUG: sendFriendRequest - Accepting existing incoming request...")
            acceptFriendRequest(targetProfile.id)
            return
        }

        // 3. Only insert if no row exists
        println("DEBUG: sendFriendRequest - Inserting new friendship row...")
        Supabase.client.postgrest["friendships"].insert(
            mapOf(
                "user_id_1" to id1,
                "user_id_2" to id2,
                "requester_id" to myId,
                "status" to "pending"
            )
        )
    }

    /**
     * Accepts a pending request from a specific friend
     */
    suspend fun acceptFriendRequest(senderId: String) {
        println("DEBUG: acceptFriendRequest - Accepting from $senderId")
        // We just call the server function.
        // It already knows who 'you' are (via auth.uid())
        // and it handles the sorting and the timestamp!
        Supabase.client.postgrest.rpc(
            function = "accept_friend",
            parameters = mapOf("sender_id" to senderId)
        )
    }

    suspend fun rejectFriendRequest(senderId: String) {
        try {
            println("DEBUG: rejectFriendRequest - Rejecting from $senderId")
            Supabase.client.postgrest.rpc(
                function = "reject_friend",
                parameters = mapOf("sender_id" to senderId)
            )
        } catch (e: Exception) {
            println("ERROR: rejectFriendRequest - Failed: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Fetches people who sent YOU a friend request
     */
    suspend fun fetchIncomingRequests(): List<Profile> {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: run {
            println("WARNING: fetchIncomingRequests - No current user ID")
            return emptyList()
        }

        println("DEBUG: fetchIncomingRequests - Fetching for $myId")
        return Supabase.client.postgrest["friend_stories"]
            .select() {
                filter {
                    eq("status", "pending")
                    neq("requester_id", myId) // Not sent by me
                    // But the friendship row MUST involve me
                    or {
                        eq("user_id_1", myId)
                        eq("user_id_2", myId)
                    }
                    // We only want the record of the OTHER person
                    neq("friend_id", myId)
                }
            }
            .decodeList<FriendUiState>()
            .map { Profile(it.id, it.name, it.playerTag) }
    }

    /**
     * The "Big Query": Fetches friends + their last session
     */
    suspend fun fetchFriendsWithStories(): List<FriendUiState> {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: run {
            println("WARNING: fetchFriendsWithStories - No current user ID")
            return emptyList()
        }

        println("DEBUG: fetchFriendsWithStories - Fetching for $myId")
        return Supabase.client.postgrest["friend_stories"]
            .select() {
                filter {
                    eq("status", "accepted")
                    // We only want the record where 'friend_id' is NOT us
                    neq("friend_id", myId)
                    // And the row must involve us
                    or {
                        eq("user_id_1", myId)
                        eq("user_id_2", myId)
                    }
                }
            }
            .decodeList<FriendUiState>()
    }
}
