package com.grepho.cozydoubling.features.inventory

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.features.shop.ThemeItemUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InventoryViewModel : ViewModel() {

    private val _ownedThemes = MutableStateFlow<List<ThemeItemUiState>>(emptyList())
    val ownedThemes: StateFlow<List<ThemeItemUiState>> = _ownedThemes.asStateFlow()

    init {
        loadInventory()
    }

    private fun loadInventory() {
        // TODO: Replace with Supabase fetch (owned themes only, join user_owned_themes -> themes)
        _ownedThemes.value = listOf(
            ThemeItemUiState(
                id = "1",
                name = "Matcha Green",
                palette = ThemePalette(
                    primary = Color(0xFF558B2F),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFDCEDC8),
                    onPrimaryContainer = Color(0xFF1B2E0E),
                    secondaryContainer = Color(0xFFE8F5C8),
                    onSecondaryContainer = Color(0xFF33691E),
                    surfaceVariant = Color(0xFFEEF5E4),
                    onSurfaceVariant = Color(0xFF495B3D),
                    onSurface = Color(0xFF1B1B1B),
                    background = Color(0xFFF7FBF1)
                ),
                leafPrice = 1000,
                iapPrice = "$0.99",
                isPremium = false,
                isOwned = true,
                isEquipped = true
            ),
            ThemeItemUiState(
                id = "4",
                name = "Cozy Cabin",
                palette = ThemePalette(
                    primary = Color(0xFF8D6E63),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFD7CCC8),
                    onPrimaryContainer = Color(0xFF3E2723),
                    secondaryContainer = Color(0xFFEFEBE9),
                    onSecondaryContainer = Color(0xFF4E342E),
                    surfaceVariant = Color(0xFFF3ECE9),
                    onSurfaceVariant = Color(0xFF5D4037),
                    onSurface = Color(0xFF3E2723),
                    background = Color(0xFFFBF7F5)
                ),
                leafPrice = 1500,
                iapPrice = "$1.99",
                isPremium = true,
                isOwned = true,
                isEquipped = false
            )
        )
    }

    // 2. Handle Actions
    fun onEquipClicked(themeId: String) {
        _ownedThemes.value = _ownedThemes.value.map { theme ->
            theme.copy(isEquipped = theme.id == themeId)
        }
        // TODO: Save this new equipped state to Supabase later
    }
}