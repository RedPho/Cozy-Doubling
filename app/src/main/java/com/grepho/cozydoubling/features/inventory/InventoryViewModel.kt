package com.grepho.cozydoubling.features.inventory

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.grepho.cozydoubling.features.shop.ThemeItemUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InventoryViewModel : ViewModel() {

    // 1. Hold the State (Only themes the user actually owns)
    private val _ownedThemes = MutableStateFlow<List<ThemeItemUiState>>(emptyList())
    val ownedThemes: StateFlow<List<ThemeItemUiState>> = _ownedThemes.asStateFlow()

    init {
        loadInventory()
    }

    private fun loadInventory() {
        // TODO: Replace with Supabase fetch from 'user_themes' table
        _ownedThemes.value = listOf(
            ThemeItemUiState("1", "Matcha Green", Color(0xFFC5E1A5), 1000, "$0.99", isPremium = false, isOwned = true, isEquipped = true),
            ThemeItemUiState("4", "Cozy Cabin", Color(0xFF8D6E63), 1500, "$1.99", isPremium = true, isOwned = true, isEquipped = false)
        )
    }

    // 2. Handle Actions
    fun onEquipClicked(themeId: String) {
        // Find the selected theme, set it to equipped, and unequip the rest
        val updatedList = _ownedThemes.value.map { theme ->
            theme.copy(isEquipped = theme.id == themeId)
        }
        _ownedThemes.value = updatedList

        // TODO: Save this new equipped state to Supabase later
    }
}