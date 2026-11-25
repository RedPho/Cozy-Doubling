package com.grepho.cozydoubling.data.remote

import android.app.Activity
import com.grepho.cozydoubling.BuildConfig
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.billing.SubscriptionOfferings
import com.grepho.cozydoubling.core.billing.SubscriptionPackage
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString

/**
 * PLACEHOLDER billing: there is no Google Play Billing / RevenueCat integration in this client yet
 * (the 1.1.0 Play build had one). Debug builds simulate purchases against a development backend;
 * release builds expose no purchasable packages, so the Shop hides Supporter passes instead of
 * offering a purchase that cannot complete. Re-integrating RevenueCat is a release blocker
 * for Supporter subscriptions (see specs/03_play_release_readiness.md).
 */
class RemoteBillingRepository(
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository,
    private val simulatePurchases: Boolean = BuildConfig.DEBUG
) : BillingRepository {

    private val samplePackages = listOf(
        SubscriptionPackage(
            identifier = "monthly_supporter",
            productId = "pass_monthly",
            formattedPrice = "$4.99"
        ),
        SubscriptionPackage(
            identifier = "yearly_supporter",
            productId = "pass_yearly",
            formattedPrice = "$39.99"
        ),
        SubscriptionPackage(
            identifier = "lifetime_supporter",
            productId = "pass_lifetime",
            formattedPrice = "$79.99"
        )
    )

    private val _offerings = MutableStateFlow<SubscriptionOfferings?>(
        SubscriptionOfferings(availablePackages = if (simulatePurchases) samplePackages else emptyList())
    )
    override val offerings: StateFlow<SubscriptionOfferings?> = _offerings.asStateFlow()

    override suspend fun purchase(activity: Activity, productId: String): Boolean {
        if (!simulatePurchases) return false
        val body = apiClient.json.encodeToString(SyncSubscriptionRequestDto(isSupporter = true))
        val result = apiClient.post("/api/subscriptions/sync", body) { }
        profileRepository.refreshProfile()
        return result.isSuccess
    }

    override suspend fun restorePurchases(): Boolean {
        // The server re-checks the entitlement; report what it decided instead of always "success".
        profileRepository.syncRestoredPurchases()
        return profileRepository.profile.value?.isSupporter == true
    }
}
