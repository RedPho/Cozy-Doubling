package com.grepho.cozydoubling.features.friends

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    // 2. Add Friend Logic
    fun onAddFriendClicked(username: String) {
        if (username.isBlank()) return

        // TODO: Replace with Supabase logic to send a friend request.
        // For now, we will just simulate adding a new friend to the local state.
        val newFriend = FriendUiState(
            name = username,
            isOnline = false,
            lastActiveText = "Added just now",
            lastTask = "Ready to focus",
            totalLeaves = 0
        )

        _friends.update { currentList ->
            currentList + newFriend
        }
    }
}