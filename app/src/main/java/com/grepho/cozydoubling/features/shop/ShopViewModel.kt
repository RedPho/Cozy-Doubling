package com.grepho.cozydoubling.features.shop

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.grepho.cozydoubling.core.theming.ThemePalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShopViewModel : ViewModel() {

    val items: StateFlow<List<ShopItemUiState>> = EconomyRepository.shopItems
        .map { rawItems ->
            val themes = rawItems.filterIsInstance<ShopItemUiState.Theme>()
            val passes = rawItems.filterIsInstance<ShopItemUiState.Pass>()

            val list = mutableListOf<ShopItemUiState>()
            if (passes.isNotEmpty()) {
                list.add(ShopItemUiState.SupporterSection(passes))
            }
            list.addAll(themes)
            list
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())




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

    fun onBuyWithCashClicked(itemId: String) {
        // TODO: Logic to trigger Google Play Billing goes here
    }
}