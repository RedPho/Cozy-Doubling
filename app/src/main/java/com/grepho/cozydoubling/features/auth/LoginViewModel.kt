package com.grepho.cozydoubling.features.auth

import androidx.lifecycle.ViewModel
import com.grepho.cozydoubling.core.Supabase
import io.github.jan.supabase.auth.auth
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    // This allows us to observe whether the user is logged in,
    // logged out, or if the session is still loading.
    val sessionStatus: Flow<SessionStatus> = Supabase.client.auth.sessionStatus

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun signInWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                Supabase.client.auth.signInWith(Email) {
                    this.email = email
                    password = pass
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message ?: "An unexpected error occurred"
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
