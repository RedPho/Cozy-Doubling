package com.grepho.cozydoubling.core.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("player_tag")
    val playerTag: String,
    @SerialName("equipped_theme_id") // Add this line
    val equippedThemeId: String? = null,
    val leaves: Long = 0,
    @SerialName("created_at")
    val createdAt: String? = null
)