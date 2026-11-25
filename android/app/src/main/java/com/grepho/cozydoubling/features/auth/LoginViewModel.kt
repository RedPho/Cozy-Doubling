package com.grepho.cozydoubling.features.auth

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.R
import com.grepho.cozydoubling.core.auth.AuthCancelledException
import com.grepho.cozydoubling.core.auth.AuthInputValidator
import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A user-facing error: either a localized validation message or a message from the server. */
sealed class LoginError {
    data class Resource(@StringRes val resId: Int) : LoginError()
    data class Message(val text: String) : LoginError()
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: LoginError? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    constructor() : this(ServiceLocator.authRepository)

    val sessionStatus: Flow<AuthStatus> = authRepository.authStatus

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signInWithEmail(email: String, password: String) {
        val problem = AuthInputValidator.validateSignIn(email, password)
        if (problem != null) {
            _uiState.update { it.copy(error = LoginError.Resource(problem.toStringRes())) }
            return
        }
        launchAuth { authRepository.signInWithEmail(email, password) }
    }

    fun register(email: String, password: String, confirmPassword: String, displayName: String) {
        val problem = AuthInputValidator.validateRegistration(email, password, confirmPassword)
        if (problem != null) {
            _uiState.update { it.copy(error = LoginError.Resource(problem.toStringRes())) }
            return
        }
        launchAuth { authRepository.registerWithEmail(email, password, displayName.ifBlank { null }) }
    }

    fun signInWithGoogle(activity: Activity) {
        launchAuth { authRepository.signInWithGoogle(activity) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun launchAuth(block: suspend () -> Result<Unit>) {
        if (_uiState.value.isLoading) return
        _uiState.value = LoginUiState(isLoading = true)
        viewModelScope.launch {
            val result = block()
            val failure = result.exceptionOrNull()
            _uiState.value = LoginUiState(
                isLoading = false,
                error = when {
                    failure == null || failure is AuthCancelledException -> null
                    failure.message.isNullOrBlank() -> LoginError.Resource(R.string.login_error_generic)
                    else -> LoginError.Message(failure.message!!)
                }
            )
        }
    }

    private fun AuthInputValidator.Problem.toStringRes(): Int = when (this) {
        AuthInputValidator.Problem.EMAIL_INVALID -> R.string.login_error_email_invalid
        AuthInputValidator.Problem.PASSWORD_EMPTY -> R.string.login_error_password_empty
        AuthInputValidator.Problem.PASSWORD_TOO_SHORT -> R.string.login_error_password_short
        AuthInputValidator.Problem.PASSWORD_TOO_LONG -> R.string.login_error_password_long
        AuthInputValidator.Problem.PASSWORDS_DIFFER -> R.string.login_error_passwords_differ
    }
}
