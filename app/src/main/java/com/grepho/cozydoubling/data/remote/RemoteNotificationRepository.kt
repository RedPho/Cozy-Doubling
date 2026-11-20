package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.notifications.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class RemoteNotificationRepository(
    private val apiClient: ApiClient
) : NotificationRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun syncToken() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result?.let { token ->
                        scope.launch {
                            updateToken(token)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("RemoteNotificationRepository: Firebase messaging token sync error: ${e.message}")
        }
    }

    override suspend fun updateToken(token: String) {
        val body = apiClient.json.encodeToString(FcmTokenRequestDto(fcmToken = token))
        apiClient.post("/api/profile/fcm-token", body) { }
    }

    override suspend fun clearToken() {
        apiClient.delete("/api/profile/fcm-token") { }
    }
}
