package com.grepho.cozydoubling.core.billing

data class SubscriptionPackage(
    val identifier: String,
    val productId: String,
    val formattedPrice: String
)

data class SubscriptionOfferings(
    val availablePackages: List<SubscriptionPackage>
)
