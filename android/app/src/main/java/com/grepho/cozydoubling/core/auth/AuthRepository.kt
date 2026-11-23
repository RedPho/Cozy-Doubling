package com.grepho.cozydoubling.core.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authStatus: StateFlow<AuthStatus>
    suspend fun signInWithEmail(email: String, pass: String): Result<Unit>
    suspend fun signInWithGoogle(): Result<Unit>
    suspend fun signOut(): Result<Unit>
    fun getCurrentUserId(): String?
}
