package com.grepho.cozydoubling.features.shop

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShopViewModel : ViewModel() {

    // 1. Hold the State
    private val _themes = MutableStateFlow<List<ThemeItemUiState>>(emptyList())
    val themes: StateFlow<List<ThemeItemUiState>> = _themes.asStateFlow()

    private val _userState = MutableStateFlow(UserMonetizationState(isSupporter = false, hasCozyPass = false))
    val userState: StateFlow<UserMonetizationState> = _userState.asStateFlow()

    init {
        // Fetch data as soon as the ViewModel is created
        loadThemes()
    }

    private fun loadThemes() {
        // TODO: Replace this with Supabase call later
        _themes.value = listOf(
            ThemeItemUiState("1", "Matcha Green", Color(0xFFC5E1A5), 1000, "$0.99", isPremium = false, isOwned = true),
            ThemeItemUiState("2", "Midnight Blue", Color(0xFF1A237E), 3000, "$1.99", isPremium = true, isOwned = false),
            ThemeItemUiState("3", "Sunset Glow", Color(0xFFFFCC80), 3000, "$1.99", isPremium = true, isOwned = false)
        )
    }

    // 2. Handle Actions
    fun onBuyWithLeavesClicked(themeId: String) {
        // Logic to deduct leaves and update ownership goes here
    }

    fun onBuyWithCashClicked(themeId: String) {
        // Logic to trigger Google Play Billing goes here
    }
}