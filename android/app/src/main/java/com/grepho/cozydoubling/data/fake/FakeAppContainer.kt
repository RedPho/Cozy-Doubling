package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.di.AppContainer
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.notifications.NotificationRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import com.grepho.cozydoubling.features.friends.FriendsRepository
import com.grepho.cozydoubling.features.room.FocusRoomRepository

class FakeAppContainer : AppContainer {
    override val authRepository: AuthRepository by lazy { FakeAuthRepository() }
    override val profileRepository: ProfileRepository by lazy { FakeProfileRepository(authRepository) }
    override val focusRoomRepository: FocusRoomRepository by lazy { FakeFocusRoomRepository(profileRepository) }
    override val economyRepository: EconomyRepository by lazy { FakeEconomyRepository(profileRepository) }
    override val friendsRepository: FriendsRepository by lazy { FakeFriendsRepository(profileRepository) }
    override val safetyRepository: SafetyRepository by lazy { FakeSafetyRepository() }
    override val notificationRepository: NotificationRepository by lazy { FakeNotificationRepository() }
    override val billingRepository: BillingRepository by lazy { FakeBillingRepository(profileRepository) }
}
