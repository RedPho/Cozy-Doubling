package com.grepho.cozydoubling.core.economy

import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import kotlinx.coroutines.flow.StateFlow

sealed class ThemeState {
    object Loading : ThemeState()
    object Default : ThemeState()
    data class Custom(val palette: ThemePalette) : ThemeState()
}

interface EconomyRepository {
    val shopItems: StateFlow<List<ShopItemUiState>>
    val themeState: StateFlow<ThemeState>

    suspend fun equipTheme(themeId: String)
    suspend fun unequipTheme()
    suspend fun purchaseWithLeaves(itemId: String)
    suspend fun fetchShopItems(): List<ShopItemUiState>
}
