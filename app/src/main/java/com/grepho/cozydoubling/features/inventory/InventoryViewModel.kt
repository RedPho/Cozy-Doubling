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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_THEME_ID = "system_default"

class InventoryViewModel : ViewModel() {

    private val _ownedThemes = MutableStateFlow<List<ShopItemUiState.Theme>>(emptyList())
    val ownedThemes: StateFlow<List<ShopItemUiState.Theme>> = _ownedThemes.asStateFlow()


    init {
        refreshInventory()
    }



    private fun refreshInventory() {
        viewModelScope.launch {
            // 1. Fetch EVERYTHING (Themes and Passes)
            val allShopItems = EconomyRepository.fetchShopItems()
            val profile = ProfileRepository.profile.value
            val isSupporter = profile?.isSupporter ?: false

            val defaultTheme = ShopItemUiState.Theme(
                id = DEFAULT_THEME_ID,
                name = "System Default",
                palette = CozyPalettes.SystemDefault,
                leafPrice = 0,
                isPremium = false,
                isOwned = true,
                isEquipped = profile?.equippedThemeId == null
            )

            // 2. Filter: Show if it's a Theme AND (User owns it OR it's a Premium Theme and user is a Supporter)
            val ownedThemes = allShopItems.filterIsInstance<ShopItemUiState.Theme>().filter { theme ->
                if (theme.isPremium) isSupporter else theme.isOwned
            }

            _ownedThemes.value = listOf(defaultTheme) + ownedThemes
        }
    }

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
            refreshInventory()
        }
    }
}