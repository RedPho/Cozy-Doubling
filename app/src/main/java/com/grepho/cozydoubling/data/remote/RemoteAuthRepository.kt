package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.profile.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString

class RemoteAuthRepository(
    private val apiClient: ApiClient
) : AuthRepository {

    private val _authStatus = MutableStateFlow<AuthStatus>(AuthStatus.NotAuthenticated)
    override val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    private var currentUserId: String? = null

    override suspend fun signInWithEmail(email: String, pass: String): Result<Unit> {
        val loginBody = apiClient.json.encodeToString(LoginRequestDto(email = email, password = pass))
        var loginResult = apiClient.post("/api/auth/login", loginBody) { body ->
            apiClient.json.decodeFromString<AuthResponseDto>(body)
        }

        // Auto-register convenience for new accounts
        if (loginResult.isFailure) {
            val regBody = apiClient.json.encodeToString(RegisterRequestDto(email = email, password = pass))
            val regResult = apiClient.post("/api/auth/register", regBody) { body ->
                apiClient.json.decodeFromString<AuthResponseDto>(body)
            }
            if (regResult.isSuccess) {
                loginResult = regResult
            }
        }

        return loginResult.fold(
            onSuccess = { authResp ->
                apiClient.authToken = authResp.accessToken
                fetchAndSetUser()
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    override suspend fun signInWithGoogle(): Result<Unit> {
        val googleToken = "mock:google:sample_google_uid:Cozy Doubler:cozy@google.com"
        val body = apiClient.json.encodeToString(GoogleAuthRequestDto(idToken = googleToken))
        val result = apiClient.post("/api/auth/google", body) { res ->
            apiClient.json.decodeFromString<AuthResponseDto>(res)
        }

        return result.fold(
            onSuccess = { authResp ->
                apiClient.authToken = authResp.accessToken
                fetchAndSetUser()
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    private suspend fun fetchAndSetUser(): Result<Unit> {
        val meResult = apiClient.get("/api/auth/me") { body ->
            apiClient.json.decodeFromString<Profile>(body)
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
        apiClient.post("/api/auth/logout", null) { }
        apiClient.authToken = null
        currentUserId = null
        _authStatus.value = AuthStatus.NotAuthenticated
        return Result.success(Unit)
    }

    override fun getCurrentUserId(): String? = currentUserId

    fun setInitialToken(token: String, userId: String) {
        apiClient.authToken = token
        currentUserId = userId
        _authStatus.value = AuthStatus.Authenticated(userId)
    }
}
