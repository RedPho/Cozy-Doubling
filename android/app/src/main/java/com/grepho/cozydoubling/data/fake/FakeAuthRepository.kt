package com.grepho.cozydoubling.data.fake

import android.app.Activity
import com.grepho.cozydoubling.core.auth.AuthInputValidator
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

    private val registeredPasswords = mutableMapOf<String, String>()

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        val normalized = AuthInputValidator.normalizeEmail(email)
        val known = registeredPasswords[normalized]
        if (known != null && known != password) {
            return Result.failure(IllegalArgumentException("Invalid email or password"))
        }
        _authStatus.value = AuthStatus.Authenticated(userIdFor(normalized))
        return Result.success(Unit)
    }

    override suspend fun registerWithEmail(email: String, password: String, displayName: String?): Result<Unit> {
        val normalized = AuthInputValidator.normalizeEmail(email)
        if (registeredPasswords.containsKey(normalized)) {
            return Result.failure(IllegalArgumentException("Email already registered"))
        }
        registeredPasswords[normalized] = password
        _authStatus.value = AuthStatus.Authenticated(userIdFor(normalized))
        return Result.success(Unit)
    }

    override suspend fun signInWithGoogle(activity: Activity): Result<Unit> {
        _authStatus.value = AuthStatus.Authenticated("user_cozy_001")
        return Result.success(Unit)
    }

    private fun userIdFor(normalizedEmail: String): String {
        val cleanName = normalizedEmail.substringBefore("@").filter { it.isLetterOrDigit() }
        return if (cleanName.isNotBlank()) "user_$cleanName" else "user_reviewer"
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
