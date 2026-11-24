package com.grepho.cozydoubling.features.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val friendsRepository: FriendsRepository = ServiceLocator.friendsRepository,
    private val safetyRepository: SafetyRepository = ServiceLocator.safetyRepository,
    private val profileRepository: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    // Filter friends and pending requests against the real-time blocked set
    val friends: StateFlow<List<FriendUiState>> = combine(
        friendsRepository.friends,
        safetyRepository.blockedUserIds
    ) { list, blockedIds ->
        list.filter { it.id !in blockedIds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = friendsRepository.friends.value.filter { it.id !in safetyRepository.blockedUserIds.value }
    )

    val pendingRequests: StateFlow<List<Profile>> = combine(
        friendsRepository.pendingRequests,
        safetyRepository.blockedUserIds
    ) { list, blockedIds ->
        list.filter { it.id !in blockedIds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = friendsRepository.pendingRequests.value.filter { it.id !in safetyRepository.blockedUserIds.value }
    )

    val profile: StateFlow<Profile?> = profileRepository.profile

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                friendsRepository.fetchFriendsWithStories()
                friendsRepository.fetchIncomingRequests()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
