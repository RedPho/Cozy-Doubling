package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(
    initialStatus: AuthStatus = AuthStatus.Authenticated("user_cozy_001")
) : AuthRepository {

    private val _authStatus = MutableStateFlow(initialStatus)
    override val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    override suspend fun restoreSession() {
        // Offline fake container retains its initial in-memory session state
    }

    override suspend fun signInWithEmail(email: String, pass: String): Result<Unit> {
        val cleanName = email.trim().substringBefore("@").filter { it.isLetterOrDigit() }.lowercase()
        val userId = if (cleanName.isNotBlank()) "user_$cleanName" else "user_reviewer"
        _authStatus.value = AuthStatus.Authenticated(userId)
        return Result.success(Unit)
    }

    override suspend fun signInWithGoogle(): Result<Unit> {
        _authStatus.value = AuthStatus.Authenticated("user_cozy_001")
        return Result.success(Unit)
    }

    override suspend fun signOut(): Result<Unit> {
        _authStatus.value = AuthStatus.NotAuthenticated
        return Result.success(Unit)
    }

    override fun getCurrentUserId(): String? {
        return when (val status = _authStatus.value) {
            is AuthStatus.Authenticated -> status.userId
            else -> null
        }
    }
}
