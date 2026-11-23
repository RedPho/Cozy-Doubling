package com.grepho.cozydoubling.features.settings

data class SettingsUiState(
    val username: String,
    val isSupporter: Boolean = false
)