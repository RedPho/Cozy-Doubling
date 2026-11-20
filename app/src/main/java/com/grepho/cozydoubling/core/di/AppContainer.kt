package com.grepho.cozydoubling.core.di

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.notifications.NotificationRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import com.grepho.cozydoubling.features.friends.FriendsRepository
import com.grepho.cozydoubling.features.room.FocusRoomRepository

interface AppContainer {
    val authRepository: AuthRepository
    val profileRepository: ProfileRepository
    val focusRoomRepository: FocusRoomRepository
    val economyRepository: EconomyRepository
    val friendsRepository: FriendsRepository
    val safetyRepository: SafetyRepository
    val notificationRepository: NotificationRepository
    val billingRepository: BillingRepository
}
