package com.grepho.cozydoubling.data.remote

import android.app.Activity
import com.grepho.cozydoubling.core.auth.AuthInputValidator
import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.data.auth.GoogleIdTokenProvider
import com.grepho.cozydoubling.data.auth.UnconfiguredGoogleIdTokenProvider
import com.grepho.cozydoubling.data.local.InMemoryTokenStore
import com.grepho.cozydoubling.data.local.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class RemoteAuthRepository(
    private val apiClient: ApiClient,
    private val tokenStore: TokenStore = InMemoryTokenStore(),
    private val onTransientRestoreFailure: () -> Unit = {},
    // Fail closed by default; DefaultAppContainer wires the real (or debug mock) provider.
    private val googleIdTokenProvider: GoogleIdTokenProvider = UnconfiguredGoogleIdTokenProvider
) : AuthRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _authStatus = MutableStateFlow<AuthStatus>(
        if (!tokenStore.loadToken().isNullOrBlank()) AuthStatus.Initializing else AuthStatus.NotAuthenticated
    )
    override val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    private var currentUserId: String? = null

    init {
        if (!tokenStore.loadToken().isNullOrBlank()) {
            scope.launch {
                restoreSession()
            }
        }
    }

    override suspend fun restoreSession() {
        val storedToken = tokenStore.loadToken()
        if (storedToken.isNullOrBlank()) {
            apiClient.authToken = null
            currentUserId = null
            _authStatus.value = AuthStatus.NotAuthenticated
            return
        }

        apiClient.authToken = storedToken
        val meResult = apiClient.get("/api/auth/me") { body ->
            apiClient.json.decodeFromString<ProfileDto>(body).toDomain()
        }
        meResult.fold(
            onSuccess = { profile ->
                currentUserId = profile.id
                _authStatus.value = AuthStatus.Authenticated(profile.id)
            },
            onFailure = { error ->
                if (error is ApiException && (error.code == 401 || error.code == 403)) {
                    tokenStore.clearToken()
                    apiClient.authToken = null
                    currentUserId = null
                    _authStatus.value = AuthStatus.NotAuthenticated
                } else {
                    // Preserve token on transient offline/server errors so the user isn't logged out.
                    _authStatus.value = AuthStatus.Initializing
                    onTransientRestoreFailure()
                }
            }
        )
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        // Passwords are sent verbatim: trimming would silently change what the user typed.
        val body = apiClient.json.encodeToString(
            LoginRequestDto(email = AuthInputValidator.normalizeEmail(email), password = password)
        )
        val result = apiClient.post("/api/auth/login", body) { res ->
            apiClient.json.decodeFromString<AuthResponseDto>(res)
        }
        return completeSignIn(result)
    }

    override suspend fun registerWithEmail(email: String, password: String, displayName: String?): Result<Unit> {
        val body = apiClient.json.encodeToString(
            RegisterRequestDto(
                email = AuthInputValidator.normalizeEmail(email),
                password = password,
                displayName = displayName?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
        val result = apiClient.post("/api/auth/register", body) { res ->
            apiClient.json.decodeFromString<AuthResponseDto>(res)
        }
        return completeSignIn(result)
    }

    override suspend fun signInWithGoogle(activity: Activity): Result<Unit> {
        val idToken = googleIdTokenProvider.requestIdToken(activity).getOrElse { return Result.failure(it) }
        val body = apiClient.json.encodeToString(GoogleAuthRequestDto(idToken = idToken))
        val result = apiClient.post("/api/auth/google", body) { res ->
            apiClient.json.decodeFromString<AuthResponseDto>(res)
        }
        return completeSignIn(result)
    }

    private suspend fun completeSignIn(tokenResult: Result<AuthResponseDto>): Result<Unit> {
        val authResp = tokenResult.getOrElse { return Result.failure(it) }
        apiClient.authToken = authResp.accessToken
        val userResult = fetchAndSetUser()
        if (userResult.isSuccess) {
            tokenStore.saveToken(authResp.accessToken)
        } else {
            apiClient.authToken = null
        }
        return userResult
    }

    private suspend fun fetchAndSetUser(): Result<Unit> {
        val meResult = apiClient.get("/api/auth/me") { body ->
            apiClient.json.decodeFromString<ProfileDto>(body).toDomain()
        }
        return meResult.fold(
            onSuccess = { profile ->
                currentUserId = profile.id
                _authStatus.value = AuthStatus.Authenticated(profile.id)
                Result.success(Unit)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    override suspend fun signOut(): Result<Unit> {
        try {
            apiClient.post("/api/auth/logout", null) { }
        } catch (_: Exception) {
            // Ensure local credentials are cleared even when offline
        } finally {
            tokenStore.clearToken()
            apiClient.authToken = null
            currentUserId = null
            _authStatus.value = AuthStatus.NotAuthenticated
        }
        return Result.success(Unit)
    }

    override fun getCurrentUserId(): String? = currentUserId

    fun setInitialToken(token: String, userId: String) {
        apiClient.authToken = token
        tokenStore.saveToken(token)
        currentUserId = userId
        _authStatus.value = AuthStatus.Authenticated(userId)
    }
}
