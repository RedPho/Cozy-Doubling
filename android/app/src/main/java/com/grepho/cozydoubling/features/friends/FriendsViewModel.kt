package com.grepho.cozydoubling.features.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.safety.SafetyRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val friendsRepository: FriendsRepository = ServiceLocator.friendsRepository,
    private val safetyRepository: SafetyRepository = ServiceLocator.safetyRepository,
    private val profileRepository: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    constructor() : this(
        friendsRepository = ServiceLocator.friendsRepository,
        safetyRepository = ServiceLocator.safetyRepository,
        profileRepository = ServiceLocator.profileRepository
    )

    // Filter out blocked users in real time across friends and incoming requests
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    fun refresh() = onRefresh(showErrorOnFailure = false)

    fun onRefresh(showErrorOnFailure: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                friendsRepository.refresh()
                profileRepository.refreshProfile()
            } catch (e: Exception) {
                e.printStackTrace()
                if (showErrorOnFailure) {
                    _uiEvents.emit(e.message ?: "Could not refresh friends. Check your connection.")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onSendRequest(tag: String) {
        if (tag.isBlank()) {
            _uiEvents.tryEmit("Please enter a friend code.")
            return
        }
        viewModelScope.launch {
            try {
                val message = friendsRepository.sendFriendRequest(tag)
                profileRepository.refreshProfile()
                _uiEvents.emit(message)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(e.message ?: "Could not send friend request")
            }
        }
    }

    fun onAcceptRequest(senderId: String) {
        viewModelScope.launch {
            try {
                friendsRepository.acceptFriendRequest(senderId)
                profileRepository.refreshProfile()
                _uiEvents.emit("Friend request accepted!")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(e.message ?: "Could not accept friend request")
            }
        }
    }

    fun onRejectRequest(senderId: String) {
        viewModelScope.launch {
            try {
                friendsRepository.rejectFriendRequest(senderId)
                profileRepository.refreshProfile()
                _uiEvents.emit("Friend request declined")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(e.message ?: "Could not decline friend request")
            }
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
            try {
                safetyRepository.blockUser(userId)
                friendsRepository.fetchFriendsWithStories()
                friendsRepository.fetchIncomingRequests()
                _uiEvents.emit("User blocked")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(e.message ?: "Could not block user")
            }
        }
    }

    fun onReportUser(userId: String, reason: String) {
        viewModelScope.launch {
            try {
                safetyRepository.reportUser(userId, reason)
                _uiEvents.emit("Report submitted. Thank you.")
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(e.message ?: "Could not submit report")
            }
        }
    }
}
