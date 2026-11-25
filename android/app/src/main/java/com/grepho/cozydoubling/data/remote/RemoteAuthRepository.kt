package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
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
    private val onTransientRestoreFailure: () -> Unit = {}
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

    // Generates a random session mock id once per repository instance so two emulators never collide on Google Sign-In
    private val mockDeviceId: String by lazy {
        java.util.UUID.randomUUID().toString().take(8)
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

    override suspend fun signInWithEmail(email: String, pass: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()
        val loginBody = apiClient.json.encodeToString(LoginRequestDto(email = cleanEmail, password = cleanPass))
        val loginResult = apiClient.post("/api/auth/login", loginBody) { body ->
            apiClient.json.decodeFromString<AuthResponseDto>(body)
        }

        // Auto-register convenience for new accounts (only when server returns 401/404)
        val finalResult = if (loginResult.isFailure) {
            val exception = loginResult.exceptionOrNull()
            if (exception is ApiException && (exception.code == 401 || exception.code == 404)) {
                val regBody = apiClient.json.encodeToString(RegisterRequestDto(email = cleanEmail, password = cleanPass))
                val regResult = apiClient.post("/api/auth/register", regBody) { body ->
                    apiClient.json.decodeFromString<AuthResponseDto>(body)
                }
                if (regResult.isSuccess) {
                    regResult
                } else {
                    val regMsg = regResult.exceptionOrNull()?.message ?: ""
                    if (regMsg.contains("already registered", ignoreCase = true)) {
                        Result.failure(java.io.IOException("Incorrect password for this account."))
                    } else {
                        regResult
                    }
                }
            } else {
                loginResult
            }
        } else {
            loginResult
        }

        return finalResult.fold(
            onSuccess = { authResp ->
                apiClient.authToken = authResp.accessToken
                tokenStore.saveToken(authResp.accessToken)
                fetchAndSetUser()
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    override suspend fun signInWithGoogle(): Result<Unit> {
        val googleToken = "mock:google:dev_$mockDeviceId:Cozy Doubler $mockDeviceId:cozy_$mockDeviceId@google.com"
        val body = apiClient.json.encodeToString(GoogleAuthRequestDto(idToken = googleToken))
        val result = apiClient.post("/api/auth/google", body) { res ->
            apiClient.json.decodeFromString<AuthResponseDto>(res)
        }

        return result.fold(
            onSuccess = { authResp ->
                apiClient.authToken = authResp.accessToken
                tokenStore.saveToken(authResp.accessToken)
                fetchAndSetUser()
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
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
