package com.grepho.cozydoubling.features.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(username = "Loading..."))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        // TODO: Fetch the current user's profile from Supabase
        _uiState.value = SettingsUiState(username = "CozyPanda")
    }

    fun onUpdateUsername(newName: String) {
        if (newName.isBlank()) return

        // TODO: Push the new username to the Supabase 'profiles' table
        _uiState.update { it.copy(username = newName) }
    }

    fun onDeleteAccount() {
        // TODO: Call Supabase Auth to delete user, then navigate to login screen
    }
}