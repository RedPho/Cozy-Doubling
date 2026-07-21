package com.grepho.cozydoubling.core.profile

import com.grepho.cozydoubling.core.Supabase
import com.revenuecat.purchases.Purchases
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

object ProfileRepository {

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. This is the local "cache" that the UI observes
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    init {
        // 2. The Reactive Engine: Observe Auth status globally
        Supabase.client.auth.sessionStatus
            .onEach { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        // Link the Supabase UID to RevenueCat
                        status.session.user?.let { user ->
                            Purchases.sharedInstance.logIn(user.id)
                        }
                        
                        refreshProfile()
                    }
                    is SessionStatus.NotAuthenticated -> {
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
        val user = Supabase.client.auth.currentUserOrNull() ?: return
        try {
            val fetchedProfile = Supabase.client.postgrest["profiles"]
                .select { filter { eq("id", user.id) } }
                .decodeSingle<Profile>()
            _profile.emit(fetchedProfile)
            println("DEBUG: Profile supporter status:" + profile.value?.isSupporter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateDisplayName(newName: String) {
        val myId = Supabase.client.auth.currentUserOrNull()?.id ?: return

        Supabase.client.postgrest["profiles"].update(
            mapOf("display_name" to newName)
        ) {
            filter { eq("id", myId) }
        }

        // Refresh instantly so every screen sees the new name
        refreshProfile()
    }

    suspend fun signOut() {
        Supabase.client.auth.signOut()
        _profile.emit(null) // Clear local cache
    }

    /**
     * Completely wipes the user's account and data from the server.
     */
    suspend fun deleteAccount() {
        // 1. Call the secure RPC to delete from the DB
        Supabase.client.postgrest.rpc("delete_own_account")

        // 2. Clear local session
        signOut()
    }
}