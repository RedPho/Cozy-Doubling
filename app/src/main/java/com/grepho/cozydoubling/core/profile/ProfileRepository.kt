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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(val profile: Profile?) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

object ProfileRepository {

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. This is the local "cache" that the UI observes
    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    // Compatibility property for existing code
    val profile: StateFlow<Profile?> = _profileState
        .map { if (it is ProfileState.Success) it.profile else null }
        .stateIn(repoScope, SharingStarted.Eagerly, null)

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
                        _profileState.emit(ProfileState.Success(null))
                    }
                    else -> { /* Loading... stay in Loading state */ }
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
            _profileState.emit(ProfileState.Success(fetchedProfile))
            println("DEBUG: refreshProfile - Profile fetched. Supporter status: ${fetchedProfile.isSupporter}")
            
            // 🚀 Signal other repositories (like Friends) to sync their data
            _syncEvents.emit(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("ERROR: refreshProfile - Failed to fetch profile: ${e.message}")
            e.printStackTrace()
            _profileState.emit(ProfileState.Error(e.message ?: "Failed to fetch profile"))
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
            // Clear FCM token before signing out
            com.grepho.cozydoubling.core.notifications.NotificationRepository.clearToken()
            
            // NEW: Wipe local task cache to prevent data leakage between accounts
            com.grepho.cozydoubling.features.room.LocalTaskDataSource.clear()

            Supabase.client.auth.signOut()
            _profileState.emit(ProfileState.Success(null)) // Clear local cache
        } catch (e: Exception) {
            println("ERROR: signOut - Failed: ${e.message}")
            e.printStackTrace()
            // Even if sign out fails on server, we should probably clear local state
            _profileState.emit(ProfileState.Success(null))
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
