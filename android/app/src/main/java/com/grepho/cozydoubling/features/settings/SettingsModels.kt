package com.grepho.cozydoubling.features.settings

import com.grepho.cozydoubling.core.safety.BlockedUser

data class SettingsUiState(
    val username: String,
    val isSupporter: Boolean = false,
    val blockedUsers: List<BlockedUser> = emptyList(),
    val isPipEnabled: Boolean = true
)