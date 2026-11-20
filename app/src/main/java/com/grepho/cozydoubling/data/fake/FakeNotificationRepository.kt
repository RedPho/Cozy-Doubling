package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.notifications.NotificationRepository

class FakeNotificationRepository : NotificationRepository {
    override fun syncToken() {
        println("FakeNotificationRepository: syncToken called")
    }

    override suspend fun updateToken(token: String) {
        println("FakeNotificationRepository: updateToken called with: $token")
    }

    override suspend fun clearToken() {
        println("FakeNotificationRepository: clearToken called")
    }
}
