package com.grepho.cozydoubling.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import com.grepho.cozydoubling.ui.theme.CozyPalettes
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DEFAULT_THEME_ID = "system_default"

class InventoryViewModel : ViewModel() {
    private fun computeOwnedThemes(): List<ShopItemUiState.Theme> {
        val allItems = EconomyRepository.shopItems.value
        val profile = ProfileRepository.profile.value
        val isSupporter = profile?.isSupporter ?: false

        val defaultTheme = ShopItemUiState.Theme(
            id = "system_default",
            name = "System Default",
            palette = CozyPalettes.SystemDefault,
            leafPrice = 0,
            isPremium = false,
            isOwned = true,
            isEquipped = profile?.equippedThemeId == null
        )

        val ownedFromShop = allItems.filterIsInstance<ShopItemUiState.Theme>().filter { theme ->
            if (theme.isPremium) isSupporter else theme.isOwned
        }

        return listOf(defaultTheme) + ownedFromShop
    }

    // 2. Use the helper for both the reactive flow AND the initial value
    val ownedThemes: StateFlow<List<ShopItemUiState.Theme>> = combine(
        EconomyRepository.shopItems,
        ProfileRepository.profile
    ) { _, _ ->
        computeOwnedThemes()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = computeOwnedThemes() // FIXED: No more emptyList()!
    )


    // 2. Handle Actions
    fun onEquipClicked(themeId: String) {
        viewModelScope.launch {
            if (themeId == DEFAULT_THEME_ID) {
                // Call the new unequip RPC
                Supabase.client.postgrest.rpc("unequip_theme")
                ProfileRepository.refreshProfile()
            } else {
                EconomyRepository.equipTheme(themeId)
            }
        }
    }
}