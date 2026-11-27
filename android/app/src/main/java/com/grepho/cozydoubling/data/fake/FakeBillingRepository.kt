package com.grepho.cozydoubling.data.fake

import android.app.Activity
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.billing.SubscriptionOfferings
import com.grepho.cozydoubling.core.billing.SubscriptionPackage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBillingRepository(
    private val profileRepository: FakeProfileRepository
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
        SubscriptionOfferings(availablePackages = samplePackages)
    )
    override val offerings: StateFlow<SubscriptionOfferings?> = _offerings.asStateFlow()

    override suspend fun purchase(activity: Activity, productId: String): Boolean {
        profileRepository.setSupporter(true)
        return true
    }

    override suspend fun restorePurchases(): Boolean {
        profileRepository.setSupporter(true)
        return true
    }
}
