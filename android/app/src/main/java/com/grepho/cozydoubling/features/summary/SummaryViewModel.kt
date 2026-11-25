package com.grepho.cozydoubling.features.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.features.room.FocusRoomRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SummaryViewModel(
    private val repository: FocusRoomRepository = ServiceLocator.focusRoomRepository,
    private val scopeOverride: CoroutineScope? = null
) : ViewModel() {

    constructor() : this(
        repository = ServiceLocator.focusRoomRepository,
        scopeOverride = null
    )

    private val scope: CoroutineScope
        get() = scopeOverride ?: viewModelScope

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    fun loadSessionStats(sessionId: String, wasInterrupted: Boolean = false) {
        _uiState.update {
            it.copy(
                isLoading = true,
                wasInterrupted = wasInterrupted,
                loadError = false
            )
        }
        scope.launch {
            try {
                val session = repository.fetchSession(sessionId)
                if (session != null && session.isProcessed) {
                    val resolvedReason = session.endReason ?: if (wasInterrupted) "app_closed" else null
                    val interrupted = wasInterrupted ||
                        resolvedReason == "app_closed" ||
                        resolvedReason == "abandoned_disconnect"
                    _uiState.update {
                        SummaryUiState(
                            focusedMinutes = session.durationMinutes,
                            tasksFinished = session.tasksCompleted,
                            leavesEarned = session.earnedLeaves.toInt(),
                            endReason = resolvedReason,
                            wasCapped = session.wasCapped,
                            isLoading = false,
                            wasInterrupted = interrupted,
                            loadError = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            wasInterrupted = wasInterrupted,
                            endReason = it.endReason ?: if (wasInterrupted) "app_closed" else null,
                            loadError = true
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        wasInterrupted = wasInterrupted,
                        endReason = it.endReason ?: if (wasInterrupted) "app_closed" else null,
                        loadError = true
                    )
                }
            }
        }
    }
}
