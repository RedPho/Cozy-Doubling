package com.grepho.cozydoubling.core.notifications

import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object NotificationRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Retrieves the current FCM token and syncs it to the Supabase profile.
     */
    fun syncToken() {
        scope.launch {
            try {
                println("DEBUG: NotificationRepository - Fetching FCM token from Firebase...")
                val token = FirebaseMessaging.getInstance().token.await()
                println("DEBUG: NotificationRepository - Received token: ${token.take(10)}...")
                updateToken(token)
            } catch (e: Exception) {
                println("ERROR: NotificationRepository - Failed to sync token: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Updates the FCM token in the Supabase 'profiles' table.
     */
    suspend fun updateToken(token: String) = withContext(Dispatchers.IO) {
        val user = Supabase.client.auth.currentUserOrNull() ?: run {
            println("WARNING: NotificationRepository - No user logged in, skipping token update")
            return@withContext
        }
        println("DEBUG: NotificationRepository - Attempting to save FCM token to Supabase...")

        try {
            // We use select() here so we can see if the server actually returns the updated row
            Supabase.client.postgrest["profiles"].update(
                {
                    set("fcm_token", token)
                }
            ) {
                filter {
                    eq("id", user.id)
                }
            }
            
            println("DEBUG: NotificationRepository - Token update call finished.")
            
            // Re-fetch profile to verify the token was actually saved
            ProfileRepository.refreshProfile()
            
            // Check the refreshed state
            val refreshedProfile = ProfileRepository.profile.value
            if (refreshedProfile?.fcmToken == token) {
                println("DEBUG: NotificationRepository - Token VERIFIED on server database.")
            } else {
                println("WARNING: NotificationRepository - Token sync appeared to succeed, but database still reports NULL or old value. Check RLS policies.")
            }
        } catch (e: Exception) {
            println("ERROR: NotificationRepository - Failed to save token to Supabase: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Clears the FCM token from the Supabase profile (useful on logout).
     */
    suspend fun clearToken() = withContext(Dispatchers.IO) {
        val user = Supabase.client.auth.currentUserOrNull() ?: return@withContext
        println("DEBUG: NotificationRepository - Clearing FCM token for user ${user.id}")

        try {
            Supabase.client.postgrest["profiles"].update(
                {
                    set("fcm_token", null as String?)
                }
            ) {
                filter {
                    eq("id", user.id)
                }
            }
            println("DEBUG: NotificationRepository - Token cleared successfully")
        } catch (e: Exception) {
            println("ERROR: NotificationRepository - Clear failed: ${e.message}")
        }
    }
}
