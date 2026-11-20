package com.grepho.cozydoubling.core.di

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.notifications.NotificationRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import com.grepho.cozydoubling.data.fake.FakeAppContainer
import com.grepho.cozydoubling.features.friends.FriendsRepository
import com.grepho.cozydoubling.features.room.FocusRoomRepository

object ServiceLocator {
    var container: AppContainer = FakeAppContainer()

    val authRepository: AuthRepository get() = container.authRepository
    val profileRepository: ProfileRepository get() = container.profileRepository
    val focusRoomRepository: FocusRoomRepository get() = container.focusRoomRepository
    val economyRepository: EconomyRepository get() = container.economyRepository
    val friendsRepository: FriendsRepository get() = container.friendsRepository
    val safetyRepository: SafetyRepository get() = container.safetyRepository
    val notificationRepository: NotificationRepository get() = container.notificationRepository
    val billingRepository: BillingRepository get() = container.billingRepository
}
