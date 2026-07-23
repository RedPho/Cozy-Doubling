package com.grepho.cozydoubling.features.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.features.room.FocusRoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SummaryViewModel : ViewModel() {
    private val repository = FocusRoomRepository()
    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    fun loadSessionStats(sessionId: String) {
        viewModelScope.launch {
            try {
                val session = repository.fetchSession(sessionId)
                if (session != null) {
                    _uiState.update {
                        SummaryUiState(
                            focusedMinutes = session.durationMinutes,
                            tasksFinished = session.tasksCompleted,
                            leavesEarned = session.earnedLeaves.toInt()
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

}