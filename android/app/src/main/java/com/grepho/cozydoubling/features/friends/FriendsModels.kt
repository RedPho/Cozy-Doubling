package com.grepho.cozydoubling.features.friends

data class FriendUiState(
    val id: String,
    val name: String,
    val playerTag: String,
    val lastSessionDuration: Int? = null,
    val lastSessionDate: String? = null,
    val lastTaskText: String? = null,
    val friendsSince: String? = null
)