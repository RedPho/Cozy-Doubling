package com.grepho.cozydoubling.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import com.grepho.cozydoubling.ui.theme.CozyPalettes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DEFAULT_THEME_ID = "system_default"

class InventoryViewModel(
    private val economyRepository: EconomyRepository = ServiceLocator.economyRepository,
    private val profileRepository: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    private fun computeOwnedThemes(): List<ShopItemUiState.Theme> {
        val allItems = economyRepository.shopItems.value
        val profile = profileRepository.profile.value
        val isSupporter = profile?.isSupporter ?: false

        val defaultTheme = ShopItemUiState.Theme(
            id = "system_default",
            name = "System Default",
            palette = CozyPalettes.SystemDefault,
            leafPrice = 0,
            isPremium = false,
            isOwned = true,
            isEquipped = profile?.equippedThemeId == null || profile.equippedThemeId == "system_default"
        )

        val ownedFromShop = allItems.filterIsInstance<ShopItemUiState.Theme>().filter { theme ->
            if (theme.id == "system_default") false
            else if (theme.isPremium) isSupporter
            else theme.isOwned
        }

        return listOf(defaultTheme) + ownedFromShop
    }

    val ownedThemes: StateFlow<List<ShopItemUiState.Theme>> = combine(
        economyRepository.shopItems,
        profileRepository.profile
    ) { _, _ ->
        computeOwnedThemes()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = computeOwnedThemes()
    )

    fun onEquipClicked(themeId: String) {
        viewModelScope.launch {
            if (themeId == DEFAULT_THEME_ID) {
                economyRepository.unequipTheme()
                profileRepository.refreshProfile()
            } else {
                economyRepository.equipTheme(themeId)
                profileRepository.refreshProfile()
            }
        }
    }
}
