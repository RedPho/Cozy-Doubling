package com.grepho.cozydoubling.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface BillingRepository {
    val offerings: StateFlow<SubscriptionOfferings?>
    suspend fun purchase(activity: Activity, productId: String): Boolean
    suspend fun restorePurchases(): Boolean
}
