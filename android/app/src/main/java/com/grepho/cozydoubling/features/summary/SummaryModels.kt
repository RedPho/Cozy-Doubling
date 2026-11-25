package com.grepho.cozydoubling.features.summary

data class SummaryUiState(
    val focusedMinutes: Int = 0,
    val tasksFinished: Int = 0,
    val leavesEarned: Int = 0,
    val endReason: String? = null,
    val wasCapped: Boolean = false,
    val isLoading: Boolean = false,
    val wasInterrupted: Boolean = false,
    val loadError: Boolean = false
)