package com.grepho.cozydoubling.features.shop

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.economy.EconomyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.grepho.cozydoubling.core.theming.ThemePalette
import kotlinx.coroutines.launch

// NOTE: mock data for now. When Supabase is wired up, swap loadThemes()
// for a call into SupabaseThemeRepository.fetchThemes() + fetchOwnedThemeIds(),
// mapping ThemeDto -> ThemeItemUiState via ThemeDto.colors.toPalette()
// (see ThemeModels.kt / SupabaseThemeRepository.kt). The ThemePalette shape
// below is already the real, grep-verified role set the whole app consumes,
// so no changes should be needed here beyond swapping the data source.
class ShopViewModel : ViewModel() {

    private val _themes = MutableStateFlow<List<ThemeItemUiState>>(emptyList())
    val themes: StateFlow<List<ThemeItemUiState>> = _themes.asStateFlow()

    private val _userState = MutableStateFlow(UserMonetizationState(isSupporter = false, hasCozyPass = false))
    val userState: StateFlow<UserMonetizationState> = _userState.asStateFlow()

    init {
        refreshShop()
    }

    fun refreshShop() {
        viewModelScope.launch {
            _themes.value = EconomyRepository.fetchThemes()
        }
    }

    // 2. Handle Actions
    fun onBuyWithLeavesClicked(themeId: String) {
        viewModelScope.launch {
            try {
                EconomyRepository.purchaseWithLeaves(themeId)
                // Refresh to show the "Owned" badge
                refreshShop()
            } catch (e: Exception) {
                e.printStackTrace()
                // TODO: Show "Not enough leaves" toast
            }
        }
    }

    fun onBuyWithCashClicked(themeId: String) {
        // TODO: Logic to trigger Google Play Billing goes here
    }
}