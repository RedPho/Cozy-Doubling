package com.grepho.cozydoubling.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    // 1. Map the real profile into the Settings UI State
    val uiState: StateFlow<SettingsUiState> = ProfileRepository.profile
        .map { profile ->
            SettingsUiState(username = profile?.displayName ?: "Loading...")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(username = "Loading...")
        )

    fun onUpdateUsername(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            try {
                ProfileRepository.updateDisplayName(newName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            ProfileRepository.signOut()
        }
    }

    fun onDeleteAccount() {
        viewModelScope.launch {
            try {
                ProfileRepository.deleteAccount()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}