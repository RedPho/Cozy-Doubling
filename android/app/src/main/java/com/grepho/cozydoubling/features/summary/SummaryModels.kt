package com.grepho.cozydoubling.features.summary

data class SummaryUiState(
    val focusedMinutes: Int = 0,
    val tasksFinished: Int = 0,
    val leavesEarned: Int = 0
)