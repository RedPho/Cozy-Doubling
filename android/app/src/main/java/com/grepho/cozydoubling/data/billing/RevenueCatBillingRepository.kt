package com.grepho.cozydoubling.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.billing.SubscriptionOfferings
import com.grepho.cozydoubling.core.billing.SubscriptionPackage
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.data.remote.ApiClient
import com.grepho.cozydoubling.data.remote.SyncSubscriptionRequestDto
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString

/**
 * Google Play purchases through RevenueCat (ported from the 1.1.0 client).
 *
 * - The RevenueCat app user ID is the backend user ID, which equals the 1.1.0 Supabase user ID
 *   for imported accounts, so existing subscriptions stay attached to the same customer.
 * - Catalog `iap_id`s are RevenueCat product IDs exactly as 1.1.0 used them: subscriptions as
 *   `productId:basePlanId` (`cozy_supporter:monthly`), one-time products plain (`cozy_supporter_lifetime`).
 * - After a purchase or restore the backend re-checks the entitlement through RevenueCat REST
 *   (`/api/subscriptions/sync`); the client never decides supporter status on a production server.
 */
class RevenueCatBillingRepository(
    context: Context,
    apiKey: String,
    authStatus: StateFlow<AuthStatus>,
    private val apiClient: ApiClient,
    private val profileRepository: ProfileRepository
) : BillingRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _offerings = MutableStateFlow<SubscriptionOfferings?>(null)
    override val offerings: StateFlow<SubscriptionOfferings?> = _offerings.asStateFlow()

    /** Raw RevenueCat offerings; packages are looked up here when buying. */
    private var rcOfferings: Offerings? = null

    init {
        if (!Purchases.isConfigured) {
            Purchases.configure(PurchasesConfiguration.Builder(context.applicationContext, apiKey).build())
        }
        // Link purchases to the signed-in backend user, as 1.1.0 did with the Supabase user.
        authStatus
            .onEach { status -> onAuthStatus(status) }
            .launchIn(scope)
    }

    private suspend fun onAuthStatus(status: AuthStatus) {
        val purchases = Purchases.sharedInstance
        try {
            when (status) {
                is AuthStatus.Authenticated -> {
                    if (purchases.appUserID != status.userId) purchases.awaitLogIn(status.userId)
                    refreshOfferings()
                }
                AuthStatus.NotAuthenticated -> if (!purchases.isAnonymous) purchases.awaitLogOut()
                AuthStatus.Initializing -> Unit
            }
        } catch (e: Exception) {
            Log.w(TAG, "RevenueCat user switch failed", e)
        }
    }

    private suspend fun refreshOfferings() {
        try {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            rcOfferings = offerings
            _offerings.value = SubscriptionOfferings(
                availablePackages = offerings.current?.availablePackages.orEmpty().map { pkg ->
                    SubscriptionPackage(
                        identifier = pkg.identifier,
                        productId = pkg.product.id,
                        formattedPrice = pkg.product.price.formatted
                    )
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not load RevenueCat offerings", e)
        }
    }

    override suspend fun purchase(activity: Activity, productId: String): Boolean {
        if (rcOfferings == null) refreshOfferings()
        val rcPackage = rcOfferings?.current?.availablePackages
            ?.find { it.product.id == productId }
            ?: return false
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, rcPackage).build()
            )
            syncWithBackend(result.customerInfo)
            true
        } catch (e: PurchasesTransactionException) {
            if (!e.userCancelled) Log.w(TAG, "Purchase failed: ${e.error.code}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Purchase failed", e)
            false
        }
    }

    override suspend fun restorePurchases(): Boolean {
        return try {
            val customerInfo = Purchases.sharedInstance.awaitRestore()
            syncWithBackend(customerInfo)
            profileRepository.profile.value?.isSupporter == true
        } catch (e: Exception) {
            Log.w(TAG, "Restore failed", e)
            false
        }
    }

    /**
     * Asks the server to verify the entitlement with RevenueCat. `is_supporter` is only honoured
     * by a development server without RevenueCat REST access; production ignores it.
     */
    private suspend fun syncWithBackend(customerInfo: CustomerInfo) {
        val hasEntitlement = customerInfo.entitlements.active.isNotEmpty()
        val body = apiClient.json.encodeToString(SyncSubscriptionRequestDto(isSupporter = hasEntitlement))
        apiClient.post("/api/subscriptions/sync", body) { }
        profileRepository.refreshProfile()
    }

    private companion object {
        const val TAG = "RevenueCatBilling"
    }
}
