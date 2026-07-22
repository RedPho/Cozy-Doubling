package com.grepho.cozydoubling.core.network

import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ConnectionStateManager {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    sealed class ConnectionState {
        object Available : ConnectionState()
        object Offline : ConnectionState()
        object ServerError : ConnectionState()
        object Refreshing : ConnectionState()
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Available)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _retryEvents = MutableSharedFlow<Unit>()
    val retryEvents: SharedFlow<Unit> = _retryEvents.asSharedFlow()

    private var lastKnownOnline: Boolean = true

    fun updateConnectivity(isOnline: Boolean) {
        lastKnownOnline = isOnline
        _state.update { 
            if (isOnline) {
                // If we were offline and now online, we don't know if server is okay yet
                // but we prefer to start as Available and let Repositories report errors
                if (it is ConnectionState.Offline) ConnectionState.Available else it
            } else {
                ConnectionState.Offline 
            }
        }
    }

    fun reportServerError() {
        _state.update { 
            if (it is ConnectionState.Offline) ConnectionState.Offline else ConnectionState.ServerError
        }
    }

    fun retry() {
        if (!lastKnownOnline) {
            _state.value = ConnectionState.Offline
            return
        }

        scope.launch {
            _state.value = ConnectionState.Refreshing
            
            // Re-trigger core repository refreshes
            ProfileRepository.refreshProfile()
            
            // Broadcast retry to other parts of the app (like FocusRoom)
            _retryEvents.emit(Unit)

            // If refreshProfile succeeds, it didn't call reportServerError.
            // If it's still Refreshing, it means it was successful enough to try resuming Available.
            _state.update { 
                if (it is ConnectionState.Refreshing) ConnectionState.Available else it
            }
        }
    }
}
