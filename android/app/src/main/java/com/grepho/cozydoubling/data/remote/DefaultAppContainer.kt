package com.grepho.cozydoubling.data.remote

import android.content.Context
import com.grepho.cozydoubling.BuildConfig
import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.di.AppContainer
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.network.ConnectionStateManager
import com.grepho.cozydoubling.core.notifications.NotificationRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.room.FocusSessionLifecycleController
import com.grepho.cozydoubling.core.safety.SafetyRepository
import com.grepho.cozydoubling.data.auth.CredentialManagerGoogleIdTokenProvider
import com.grepho.cozydoubling.data.auth.DebugMockGoogleIdTokenProvider
import com.grepho.cozydoubling.data.auth.GoogleIdTokenProvider
import com.grepho.cozydoubling.data.billing.RevenueCatBillingRepository
import com.grepho.cozydoubling.data.fake.NoOpFocusSessionLifecycleController
import com.grepho.cozydoubling.data.local.InMemoryTokenStore
import com.grepho.cozydoubling.data.local.TokenStore
import com.grepho.cozydoubling.features.friends.FriendsRepository
import com.grepho.cozydoubling.features.room.FocusRoomRepository

class DefaultAppContainer(
    val baseUrl: String = BuildConfig.API_BASE_URL,
    private val tokenStore: TokenStore = InMemoryTokenStore(),
    override val focusSessionController: FocusSessionLifecycleController = NoOpFocusSessionLifecycleController(),
    private val googleIdTokenProvider: GoogleIdTokenProvider = defaultGoogleIdTokenProvider(),
    /** Needed for RevenueCat; without it (JVM tests) billing falls back to [RemoteBillingRepository]. */
    private val appContext: Context? = null,
    private val revenueCatApiKey: String = BuildConfig.REVENUECAT_APIKEY
) : AppContainer {
    val apiClient: ApiClient by lazy { ApiClient(baseUrl = baseUrl) }

    override val authRepository: AuthRepository by lazy {
        RemoteAuthRepository(
            apiClient = apiClient,
            tokenStore = tokenStore,
            onTransientRestoreFailure = { ConnectionStateManager.reportServerError() },
            googleIdTokenProvider = googleIdTokenProvider
        )
    }
    override val profileRepository: ProfileRepository by lazy { RemoteProfileRepository(apiClient, authRepository) }
    override val focusRoomRepository: FocusRoomRepository by lazy { RemoteFocusRoomRepository(apiClient, profileRepository) }
    override val economyRepository: EconomyRepository by lazy { RemoteEconomyRepository(apiClient, profileRepository) }
    override val friendsRepository: FriendsRepository by lazy { RemoteFriendsRepository(apiClient, profileRepository, authRepository) }
    override val safetyRepository: SafetyRepository by lazy { RemoteSafetyRepository(apiClient, authRepository) }
    override val notificationRepository: NotificationRepository by lazy { RemoteNotificationRepository(apiClient) }
    override val billingRepository: BillingRepository by lazy {
        val context = appContext
        if (context != null && revenueCatApiKey.isNotBlank()) {
            RevenueCatBillingRepository(
                context = context,
                apiKey = revenueCatApiKey,
                authStatus = authRepository.authStatus,
                apiClient = apiClient,
                profileRepository = profileRepository
            )
        } else {
            // No RevenueCat key: simulated purchases in debug, none in release (release needs the key).
            RemoteBillingRepository(apiClient, profileRepository)
        }
    }
}

/**
 * Real Google Sign-In whenever a web client ID is configured. Without one, debug builds use mock
 * tokens accepted only by a development backend; release builds cannot be packaged (see build.gradle.kts).
 */
internal fun defaultGoogleIdTokenProvider(): GoogleIdTokenProvider =
    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank() && BuildConfig.DEBUG) {
        DebugMockGoogleIdTokenProvider()
    } else {
        CredentialManagerGoogleIdTokenProvider(BuildConfig.GOOGLE_WEB_CLIENT_ID)
    }
