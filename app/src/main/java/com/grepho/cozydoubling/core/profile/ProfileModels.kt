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
    val leaves: Long = 1000,
    @SerialName("created_at")
    val createdAt: String? = null
)