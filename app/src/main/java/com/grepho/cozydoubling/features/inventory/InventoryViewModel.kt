package com.grepho.cozydoubling.features.inventory

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.features.shop.ThemeItemUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

            // 2. Filter to show only the ones the user actually owns
            _ownedThemes.value = allThemes.filter { it.isOwned }
        }
    }

    // 2. Handle Actions
    fun onEquipClicked(themeId: String) {
        viewModelScope.launch {
            try {
                EconomyRepository.equipTheme(themeId)
                refreshInventory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}