package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.di.AppContainer
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.notifications.NotificationRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.room.FocusSessionLifecycleController
import com.grepho.cozydoubling.core.safety.SafetyRepository
import com.grepho.cozydoubling.features.friends.FriendsRepository
import com.grepho.cozydoubling.features.room.FocusRoomRepository

class FakeAppContainer : AppContainer {
    override val authRepository: AuthRepository by lazy { FakeAuthRepository() }
    private val fakeProfileRepository: FakeProfileRepository by lazy { FakeProfileRepository(authRepository) }
    override val profileRepository: ProfileRepository get() = fakeProfileRepository
    override val focusRoomRepository: FocusRoomRepository by lazy { FakeFocusRoomRepository(fakeProfileRepository) }
    override val economyRepository: EconomyRepository by lazy { FakeEconomyRepository(fakeProfileRepository) }
    override val safetyRepository: SafetyRepository by lazy { FakeSafetyRepository() }
    override val friendsRepository: FriendsRepository by lazy { FakeFriendsRepository(profileRepository, safetyRepository) }
    override val notificationRepository: NotificationRepository by lazy { FakeNotificationRepository() }
    override val billingRepository: BillingRepository by lazy { FakeBillingRepository(fakeProfileRepository) }
    override val focusSessionController: FocusSessionLifecycleController by lazy { NoOpFocusSessionLifecycleController() }
}
