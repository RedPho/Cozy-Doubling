package com.grepho.cozydoubling.features.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val friendsRepository: FriendsRepository = ServiceLocator.friendsRepository,
    private val safetyRepository: SafetyRepository = ServiceLocator.safetyRepository,
    private val profileRepository: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    // 1. We keep two lists now: real friends and pending requests
    val friends: StateFlow<List<FriendUiState>> = friendsRepository.friends
    val pendingRequests: StateFlow<List<Profile>> = friendsRepository.pendingRequests
    val profile: StateFlow<Profile?> = profileRepository.profile

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    fun onSendRequest(tag: String) {
        if (tag.isBlank()) return
        viewModelScope.launch {
            try {
                friendsRepository.sendFriendRequest(tag)
                profileRepository.refreshProfile()
                _uiEvents.emit("Friend request sent!")
            } catch (e: Exception) { 
                e.printStackTrace() 
                _uiEvents.emit(e.message ?: "Could not send request")
            }
        }
    }

    fun onAcceptRequest(senderId: String) {
        viewModelScope.launch {
            try {
                friendsRepository.acceptFriendRequest(senderId)
                profileRepository.refreshProfile()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun onRejectRequest(senderId: String) {
        viewModelScope.launch {
            try {
                friendsRepository.rejectFriendRequest(senderId)
                profileRepository.refreshProfile()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun onRemoveFriend(friendId: String) {
        viewModelScope.launch {
            try {
                friendsRepository.removeFriend(friendId)
                profileRepository.refreshProfile()
                _uiEvents.emit("Friend removed")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(e.message ?: "Could not remove friend")
            }
        }
    }

    fun onBlockUser(userId: String) {
        viewModelScope.launch {
            safetyRepository.blockUser(userId)
        }
    }

    fun onReportUser(userId: String, reason: String) {
        viewModelScope.launch {
            safetyRepository.reportUser(userId, reason)
            _uiEvents.emit("Report submitted. Thank you.")
        }
    }
}
