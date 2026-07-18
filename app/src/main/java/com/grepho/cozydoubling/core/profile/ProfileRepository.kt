package com.grepho.cozydoubling.core.profile

import com.grepho.cozydoubling.core.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileRepository {

    // 1. This is the local "cache" that the UI observes
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    // 2. The manual "Refresh" action
    suspend fun refreshProfile() {
        val user = Supabase.client.auth.currentUserOrNull()
        if (user == null) {
            println("DEBUG: ProfileRepo - User is NULL. Session might not be ready.")
            return
        }
        println("DEBUG: ProfileRepo - Fetching profile for UID: ${user.id}")

        try {
            val fetchedProfile = Supabase.client.postgrest["profiles"]
                .select {
                    filter {
                        eq("id", user.id)
                    }
                }
                .decodeSingle<Profile>()
            println("DEBUG: ProfileRepo - Successfully fetched: $fetchedProfile")
            _profile.emit(fetchedProfile) // Update the UI instantly
        } catch (e: Exception) {
            println("DEBUG: ProfileRepo - Error fetching profile: ${e.message}")
            e.printStackTrace()
        }
    }
}