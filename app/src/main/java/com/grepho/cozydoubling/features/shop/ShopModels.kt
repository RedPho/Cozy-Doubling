package com.grepho.cozydoubling.features.shop

import androidx.compose.ui.graphics.Color

// Represents the user's current subscription/supporter status
data class UserMonetizationState(
    val isSupporter: Boolean,
    val hasCozyPass: Boolean
)

data class ThemeItemUiState(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val leafPrice: Int,
    val iapPrice: String,
    val isPremium: Boolean,
    val isOwned: Boolean,
    val isEquipped: Boolean = false
)