package com.grepho.cozydoubling.features.journey

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JourneyViewModel : ViewModel() {

    // Initialize with a loading/default state
    private val _uiState = MutableStateFlow(
        ProfileUiState(
            username = "Loading...",
            bio = "",
            totalLeaves = 0,
            totalFocusHours = 0
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfileStats()
    }

    private fun loadProfileStats() {
        // TODO: Replace with Supabase fetch from 'profiles' table
        _uiState.value = ProfileUiState(
            username = "CozyPanda",
            bio = "Just here to get things done slowly.",
            totalLeaves = 1450,
            totalFocusHours = 42
        )
    }
}