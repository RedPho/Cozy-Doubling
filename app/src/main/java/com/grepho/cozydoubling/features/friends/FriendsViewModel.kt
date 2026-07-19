package com.grepho.cozydoubling.features.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.profile.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FriendsViewModel : ViewModel() {

    // 1. We keep two lists now: real friends and pending requests
    private val _friends = MutableStateFlow<List<FriendUiState>>(emptyList())
    val friends: StateFlow<List<FriendUiState>> = _friends.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<Profile>>(emptyList())
    val pendingRequests: StateFlow<List<Profile>> = _pendingRequests.asStateFlow()

    init {
        refreshSocialData()
    }

    /**
     * Fetches everything from Supabase and updates the UI
     */
    fun refreshSocialData() {
        viewModelScope.launch {
            try {
                val friendsList = FriendsRepository.fetchFriendsWithStories()
                println("DEBUG: Social - Found ${friendsList.size} friends")

                val pendingList = FriendsRepository.fetchIncomingRequests()
                println("DEBUG: Social - Found ${pendingList.size} incoming requests") // CHECK THIS LOG!

                _friends.emit(friendsList)
                _pendingRequests.emit(pendingList)
            } catch (e: Exception) {
                println("DEBUG: Social - Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun onSendRequest(tag: String) {
        if (tag.isBlank()) return
        viewModelScope.launch {
            try {
                FriendsRepository.sendFriendRequest(tag)
                // We refresh so the "Sent" status is reflected if needed
                refreshSocialData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onAcceptRequest(senderId: String) {
        println("DEBUG: ViewModel - Attempting to accept request from $senderId")
        viewModelScope.launch {
            try {
                FriendsRepository.acceptFriendRequest(senderId)
                println("DEBUG: ViewModel - Accept successful!")
                refreshSocialData()
            } catch (e: Exception) {
                println("DEBUG: ViewModel - Accept FAILED: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}