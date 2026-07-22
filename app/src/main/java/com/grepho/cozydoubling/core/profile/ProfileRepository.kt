package com.grepho.cozydoubling.core.profile

import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.network.ConnectionStateManager
import com.revenuecat.purchases.Purchases
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

object ProfileRepository {

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. This is the local "cache" that the UI observes
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _syncEvents = MutableSharedFlow<Unit>(replay = 0)
    val syncEvents: SharedFlow<Unit> = _syncEvents.asSharedFlow()

    init {
        // 2. The Reactive Engine: Observe Auth status globally
        Supabase.client.auth.sessionStatus
            .onEach { status ->
                println("DEBUG: ProfileRepository - Session status: $status")
                when (status) {
                    is SessionStatus.Authenticated -> {
                        // Link the Supabase UID to RevenueCat
                        status.session.user?.let { user ->
                            println("DEBUG: ProfileRepository - Authenticated as ${user.id}")
                            Purchases.sharedInstance.logIn(user.id)
                        }
                        
                        refreshProfile()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        println("DEBUG: ProfileRepository - Not authenticated")
                        // Log out of RevenueCat to protect privacy
                        Purchases.sharedInstance.logOut()
                        _profile.emit(null)
                    }
                    else -> { /* Loading... */ }
                }
            }
            .launchIn(repoScope)
    }

    // 2. The manual "Refresh" action
    suspend fun refreshProfile() {
        val user = Supabase.client.auth.currentUserOrNull() ?: run {
            println("WARNING: refreshProfile - No current user")
            return
        }
        
        try {
            println("DEBUG: refreshProfile - Fetching profile for ${user.id}")
            val fetchedProfile = Supabase.client.postgrest["profiles"]
                .select { filter { eq("id", user.id) } }
                .decodeSingle<Profile>()
            _profile.emit(fetchedProfile)
            println("DEBUG: refreshProfile - Profile fetched. Supporter status: ${fetchedProfile.isSupporter}")
            
            // 🚀 Signal other repositories (like Friends) to sync their data
            _syncEvents.emit(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("ERROR: refreshProfile - Failed to fetch profile: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
        }
    }

    suspend fun updateDisplayName(newName: String) {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: run {
            println("ERROR: updateDisplayName - No current user ID")
            return
        }

        try {
            println("DEBUG: updateDisplayName - Changing name for $myId to $newName")
            Supabase.client.postgrest["profiles"].update(
                mapOf("display_name" to newName)
            ) {
                filter { eq("id", myId) }
            }

            // Refresh instantly so every screen sees the new name
            refreshProfile()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("ERROR: updateDisplayName - Failed to update name: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
        }
    }

    suspend fun signOut() {
        try {
            println("DEBUG: ProfileRepository - Signing out...")
            Supabase.client.auth.signOut()
            _profile.emit(null) // Clear local cache
        } catch (e: Exception) {
            println("ERROR: signOut - Failed: ${e.message}")
            e.printStackTrace()
            // Even if sign out fails on server, we should probably clear local state
            _profile.emit(null)
        }
    }

    /**
     * Forces the backend to synchronize its subscription status with RevenueCat.
     */
    suspend fun triggerBackendRestoreSync() {
        try {
            println("DEBUG: triggerBackendRestoreSync - Triggering sync-revenuecat")
            // This Edge Function should be implemented on the backend to fetch the latest
            // status from RevenueCat and update the 'profiles' table.
            Supabase.client.functions.invoke("sync-revenuecat")
            
            // Refresh local profile to get the updated status
            refreshProfile()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("ERROR: triggerBackendRestoreSync - Failed: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
        }
    }

    /**
     * Completely wipes the user's account and data from the server.
     */
    suspend fun deleteAccount() {
        try {
            println("DEBUG: deleteAccount - Requesting account deletion...")
            // 1. Call the secure RPC to delete from the DB
            Supabase.client.postgrest.rpc("delete_own_account")

            // 2. Clear local session
            signOut()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("ERROR: deleteAccount - Failed: ${e.message}")
            e.printStackTrace()
            ConnectionStateManager.reportServerError()
        }
    }
}
