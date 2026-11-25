package com.grepho.cozydoubling.core.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authStatus: StateFlow<AuthStatus>
    suspend fun restoreSession()

    /** Signs in an existing email/password account. Never creates an account implicitly. */
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>

    /** Creates an email/password account ([AUTH-08]) and signs it in. */
    suspend fun registerWithEmail(email: String, password: String, displayName: String?): Result<Unit>

    /** Runs the platform Google Sign-In UI. Fails with [AuthCancelledException] when dismissed. */
    suspend fun signInWithGoogle(activity: Activity): Result<Unit>

    suspend fun signOut(): Result<Unit>
    fun getCurrentUserId(): String?
}

/** The user dismissed a sign-in prompt; not an error worth showing. */
class AuthCancelledException : Exception("Sign-in cancelled")
