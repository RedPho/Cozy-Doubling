package com.grepho.cozydoubling.features.shop

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShopViewModel(
    private val economyRepository: EconomyRepository = ServiceLocator.economyRepository,
    private val billingRepository: BillingRepository = ServiceLocator.billingRepository,
    private val profileRepository: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    val items: StateFlow<List<ShopItemUiState>> = combine(
        economyRepository.shopItems,
        billingRepository.offerings
    ) { rawItems, offerings ->
        val availablePackages = offerings?.availablePackages ?: emptyList()

        rawItems.map { item ->
            if (item is ShopItemUiState.Pass) {
                val pkg = availablePackages.find { it.productId == item.iapId }
                item.copy(priceString = pkg?.formattedPrice ?: item.priceString)
            } else item
        }.let { hydratedList ->
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

    val isSupporter: StateFlow<Boolean> = profileRepository.profile
        .map { it?.isSupporter ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun onBuyWithLeavesClicked(itemId: String) {
        viewModelScope.launch {
            try {
                economyRepository.purchaseWithLeaves(itemId)
                profileRepository.refreshProfile()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onBuyWithCashClicked(activity: Activity, itemId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val currentItems = items.value
            val pass = currentItems.filterIsInstance<ShopItemUiState.SupporterSection>()
                .firstOrNull()?.passes?.find { it.id == itemId } ?: return@launch

            val success = billingRepository.purchase(activity, pass.iapId)
            if (success) {
                onResult("Success! Your Supporter status is being updated...")
                repeat(2) {
                    delay(500)
                    profileRepository.refreshProfile()
                }
            } else {
                onResult("Purchase cancelled or failed.")
            }
        }
    }
}
