package com.grepho.cozydoubling.core.billing

import android.app.Activity
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BillingRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _offerings = MutableStateFlow<Offerings?>(null)
    val offerings: StateFlow<Offerings?> = _offerings.asStateFlow()

    init {
        // Pre-fetch offerings as soon as the app starts
        scope.launch {
            try {
                _offerings.emit(Purchases.sharedInstance.awaitOfferings())
                val offerings = offerings.value ?: return@launch
                print("------------------------------------------------------------------------")
                print(offerings)
                print("------------------------------------------------------------------------")

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun purchase(activity: Activity, rcPackage: Package): Boolean {
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, rcPackage).build()
            )
            // If the user successfully purchased, return true
            result.customerInfo.entitlements.active.isNotEmpty()
        } catch (e: Exception) {
            false // Handle cancellation or error
        }
    }
}