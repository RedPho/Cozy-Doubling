package com.grepho.cozydoubling.features.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class JourneyViewModel(
    private val profileRepository: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = profileRepository.profile
        .map { profile ->
            ProfileUiState(
                username = profile?.displayName ?: "Loading...",
                bio = "Cozy Doubler since ${profile?.createdAt?.take(7) ?: "..."}",
                totalLeaves = profile?.leaves?.toInt() ?: 0,
                totalFocusMinutes = profile?.totalMinutesFocused?.toInt() ?: 0
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = profileRepository.profile.value?.let { profile ->
                ProfileUiState(
                    username = profile.displayName,
                    bio = "Cozy Doubler since ${profile.createdAt?.take(7)}",
                    totalLeaves = profile.leaves.toInt(),
                    totalFocusMinutes = profile.totalMinutesFocused.toInt()
                )
            } ?: ProfileUiState("", "", 0, 0)
        )
}
