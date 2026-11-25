package com.grepho.cozydoubling.core.di

import android.content.Context
import com.grepho.cozydoubling.BuildConfig
import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.notifications.NotificationRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.room.FocusSessionLifecycleController
import com.grepho.cozydoubling.core.safety.SafetyRepository
import com.grepho.cozydoubling.data.fake.FakeAppContainer
import com.grepho.cozydoubling.data.local.AndroidFocusSessionController
import com.grepho.cozydoubling.data.local.EncryptedTokenStore
import com.grepho.cozydoubling.data.remote.DefaultAppContainer
import com.grepho.cozydoubling.features.friends.FriendsRepository
import com.grepho.cozydoubling.features.room.FocusRoomRepository

object ServiceLocator {
    var container: AppContainer = DefaultAppContainer()
    private var appContext: Context? = null
    private var isInitializedWithContext = false

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (isInitializedWithContext || container is FakeAppContainer) return
        val baseUrl = (container as? DefaultAppContainer)?.baseUrl ?: BuildConfig.API_BASE_URL
        container = DefaultAppContainer(
            baseUrl = baseUrl,
            tokenStore = EncryptedTokenStore(app),
            focusSessionController = AndroidFocusSessionController(app)
        )
        isInitializedWithContext = true
    }

    fun useFakeContainer() {
        container = FakeAppContainer()
    }

    fun useDefaultContainer(baseUrl: String? = null) {
        val ctx = appContext
        val resolvedBaseUrl = baseUrl ?: BuildConfig.API_BASE_URL
        container = if (ctx != null) {
            isInitializedWithContext = true
            DefaultAppContainer(
                baseUrl = resolvedBaseUrl,
                tokenStore = EncryptedTokenStore(ctx),
                focusSessionController = AndroidFocusSessionController(ctx)
            )
        } else {
            isInitializedWithContext = false
            DefaultAppContainer(baseUrl = resolvedBaseUrl)
        }
    }

    val authRepository: AuthRepository get() = container.authRepository
    val profileRepository: ProfileRepository get() = container.profileRepository
    val focusRoomRepository: FocusRoomRepository get() = container.focusRoomRepository
    val economyRepository: EconomyRepository get() = container.economyRepository
    val friendsRepository: FriendsRepository get() = container.friendsRepository
    val safetyRepository: SafetyRepository get() = container.safetyRepository
    val notificationRepository: NotificationRepository get() = container.notificationRepository
    val billingRepository: BillingRepository get() = container.billingRepository
    val focusSessionController: FocusSessionLifecycleController get() = container.focusSessionController
}
