package com.grepho.cozydoubling.core.profile

data class Profile(
    val id: String,
    val displayName: String,
    val playerTag: String,
    val equippedThemeId: String? = null,
    val leaves: Long = 0,
    val createdAt: String? = null,
    val totalMinutesFocused: Long = 0,
    val isSupporter: Boolean = false,
    val fcmToken: String? = null
)