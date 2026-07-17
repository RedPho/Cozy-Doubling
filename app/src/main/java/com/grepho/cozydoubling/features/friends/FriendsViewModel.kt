package com.grepho.cozydoubling.features.friends

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FriendsViewModel : ViewModel() {

    // 1. Hold the State
    private val _friends = MutableStateFlow<List<FriendUiState>>(emptyList())
    val friends: StateFlow<List<FriendUiState>> = _friends.asStateFlow()

    init {
        loadFriends()
    }

    private fun loadFriends() {
        // TODO: Replace with Supabase fetch from 'friendships' and 'profiles' tables
        _friends.value = listOf(
            FriendUiState(
                name = "Alex",
                isOnline = true,
                lastActiveText = "Focusing",
                lastTask = "Writing email drafts",
                totalLeaves = 450
            ),
            FriendUiState(
                name = "Sam",
                isOnline = false,
                lastActiveText = "Resting",
                lastTask = "Organized the desk",
                totalLeaves = 1200
            ),
            FriendUiState(
                name = "Jamie",
                isOnline = true,
                lastActiveText = "Focusing",
                lastTask = "Reading chapter 4",
                totalLeaves = 85
            )
        )
    }
}