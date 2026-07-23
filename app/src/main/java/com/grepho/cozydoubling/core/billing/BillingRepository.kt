package com.grepho.cozydoubling.core.billing

import android.app.Activity
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
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
                val currentOfferings = offerings.value ?: return@launch
                
                println("BILLING_DEBUG: Offering update received.")
                val current = currentOfferings.current
                if (current == null) {
                    println("BILLING_DEBUG: NO CURRENT OFFERING SET IN REVENUECAT")
                } else {
                    println("BILLING_DEBUG: Current Offering ID: ${current.identifier}")
                    current.availablePackages.forEach { pkg ->
                        println("BILLING_DEBUG: Found Package: ${pkg.identifier}, Product: ${pkg.product.id}")
                    }
                }

            } catch (e: Exception) {
                println("BILLING_DEBUG: Error fetching offerings: ${e.message}")
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

    suspend fun restorePurchases(): Boolean {
        val currentUserId = Purchases.sharedInstance.appUserID
        println("DEBUG: Starting Restore for ID: $currentUserId")

        return try {
            val result = Purchases.sharedInstance.awaitRestore()
            val hasEntitlement = result.entitlements.active.isNotEmpty()

            println("DEBUG: Restore Finished. Active Entitlements: ${result.entitlements.active.keys}")
            hasEntitlement
        } catch (e: Exception) {
            println("DEBUG: Restore Failed with Error: ${e.message}")
            false
        }
    }
}
