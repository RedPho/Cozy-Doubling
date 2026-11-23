package com.grepho.cozydoubling.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    constructor() : this(ServiceLocator.authRepository)

    val sessionStatus: Flow<AuthStatus> = authRepository.authStatus

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun signInWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _error.value = null
            val result = authRepository.signInWithEmail(email, pass)
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "An unexpected error occurred"
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _error.value = null
            val result = authRepository.signInWithGoogle()
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Google sign-in failed"
            }
        }
    }

    fun setError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }
}
