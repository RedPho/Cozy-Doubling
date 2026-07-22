package com.grepho.cozydoubling.features.shop

import android.app.Activity
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.grepho.cozydoubling.core.theming.ThemePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShopViewModel : ViewModel() {

    val items: StateFlow<List<ShopItemUiState>> = combine(
        EconomyRepository.shopItems,
        BillingRepository.offerings
    ) { rawItems, offerings ->
        // Find the RevenueCat packages
        val rcPackages = offerings?.current?.availablePackages ?: emptyList()

        rawItems.map { item ->
            if (item is ShopItemUiState.Pass) {
                // Find the real price for this pass
                val rcPackage = rcPackages.find { it.product.id == item.iapId }
                item.copy(priceString = rcPackage?.product?.price?.formatted ?: "...")
            } else item
        }.let { hydratedList ->
            // Now group into sections
            val themes = hydratedList.filterIsInstance<ShopItemUiState.Theme>()
            val passes = hydratedList.filterIsInstance<ShopItemUiState.Pass>()
                .sortedBy { pass ->
                    when {
                        pass.name.contains("Monthly", ignoreCase = true) -> 1
                        pass.name.contains("Yearly", ignoreCase = true) -> 2
                        pass.name.contains("Lifetime", ignoreCase = true) -> 3
                        else -> 4
                    }
                }

            val finalResult = mutableListOf<ShopItemUiState>()
            if (passes.isNotEmpty()) {
                finalResult.add(ShopItemUiState.SupporterSection(passes))
            }
            finalResult.addAll(themes)
            finalResult
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    val isSupporter: StateFlow<Boolean> = ProfileRepository.profile
        .map { it?.isSupporter ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )



    // 2. Handle Actions
    fun onBuyWithLeavesClicked(itemId: String) {
        viewModelScope.launch {
            try {
                EconomyRepository.purchaseWithLeaves(itemId)
                // This triggers the repo to re-fetch and all UIs update automatically!
                ProfileRepository.refreshProfile()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun onBuyWithCashClicked(activity: Activity, itemId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val currentItems = items.value
            // 1. Find the Pass in our list to get its real 'iapId'
            val pass = currentItems.filterIsInstance<ShopItemUiState.SupporterSection>()
                .firstOrNull()?.passes?.find { it.id == itemId } ?: return@launch

            val offerings = BillingRepository.offerings.value ?: return@launch
            // 2. Now find the RevenueCat package using the IAP ID
            val rcPackage = offerings.current?.availablePackages?.find { it.product.id == pass.iapId } ?: return@launch

            val success = BillingRepository.purchase(activity, rcPackage)
            if (success) {
                onResult("Success! Your Supporter status is being updated...")
                repeat(3) {
                    delay(1000)
                    ProfileRepository.refreshProfile()
                }
            } else {
                onResult("Purchase cancelled or failed.")
            }
        }
    }
}
