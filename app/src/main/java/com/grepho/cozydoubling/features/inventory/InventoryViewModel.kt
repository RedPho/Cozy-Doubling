package com.grepho.cozydoubling.features.inventory

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.features.shop.ThemeItemUiState
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_THEME_ID = "system_default"

class InventoryViewModel : ViewModel() {

    private val _ownedThemes = MutableStateFlow<List<ThemeItemUiState>>(emptyList())
    val ownedThemes: StateFlow<List<ThemeItemUiState>> = _ownedThemes.asStateFlow()

    init {
        refreshInventory()
    }



    private fun refreshInventory() {
        viewModelScope.launch {
            // 1. Fetch all themes from the repository
            val allThemes = EconomyRepository.fetchThemes()
            val profile = ProfileRepository.profile.value

            val defaultTheme = ThemeItemUiState(
                id = DEFAULT_THEME_ID,
                name = "System Default",
                palette = ThemePalette(
                    // Use some neutral colors for the preview
                    primary = Color.White,
                    onPrimary = Color.White,
                    primaryContainer = Color.White,
                    onPrimaryContainer = Color.White,
                    secondaryContainer = Color.White,
                    onSecondaryContainer = Color.White,
                    surfaceVariant = Color.White,
                    onSurfaceVariant = Color.White,
                    onSurface = Color.White,
                    background = Color.White
                ),
                leafPrice = 0,
                iapPrice = "",
                isPremium = false,
                isOwned = true,
                isEquipped = profile?.equippedThemeId == null // It's equipped if no other ID is set
            )

            // 2. Filter to show only the ones the user actually owns
            _ownedThemes.value = listOf(defaultTheme) + allThemes.filter { it.isOwned }
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