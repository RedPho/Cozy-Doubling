package com.grepho.cozydoubling.features.summary

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SummaryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        SummaryUiState(
            focusedMinutes = 125, // Example: 2 hours 5 mins
            tasksFinished = 3,
            leavesEarned = 15
        )
    )
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    // TODO: Later, we will add a function here like `fun finalizeSession(...)`
    // to accept the real stats from the FocusRoom and write them to Supabase.
}