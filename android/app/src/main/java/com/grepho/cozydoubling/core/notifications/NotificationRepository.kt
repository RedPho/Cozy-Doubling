package com.grepho.cozydoubling.core.notifications

interface NotificationRepository {
    fun syncToken()
    suspend fun updateToken(token: String)
    suspend fun clearToken()
}
