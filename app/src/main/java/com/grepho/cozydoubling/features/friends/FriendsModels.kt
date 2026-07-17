package com.grepho.cozydoubling.features.friends

data class FriendUiState(
    val name: String,
    val isOnline: Boolean,
    val lastActiveText: String, // e.g., "Online now", "Resting", "Active 2h ago"
    val lastTask: String,
    val totalLeaves: Int
)