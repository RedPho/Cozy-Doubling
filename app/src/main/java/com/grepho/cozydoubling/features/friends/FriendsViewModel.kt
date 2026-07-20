package com.grepho.cozydoubling.features.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FriendsViewModel : ViewModel() {

    // 1. We keep two lists now: real friends and pending requests
    val friends = FriendsRepository.friends
    val pendingRequests = FriendsRepository.pendingRequests


    fun onSendRequest(tag: String) {
        if (tag.isBlank()) return
        viewModelScope.launch {
            try {
                FriendsRepository.sendFriendRequest(tag)
                // Trigger a refresh by updating the profile (our chain reaction)
                ProfileRepository.refreshProfile()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun onAcceptRequest(senderId: String) {
        viewModelScope.launch {
            try {
                FriendsRepository.acceptFriendRequest(senderId)
                ProfileRepository.refreshProfile()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}