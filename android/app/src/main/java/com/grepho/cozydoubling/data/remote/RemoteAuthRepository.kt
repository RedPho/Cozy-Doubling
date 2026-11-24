package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
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

    // Generates a random session mock id once per repository instance so two emulators never collide on Google Sign-In
    private val mockDeviceId: String by lazy {
        java.util.UUID.randomUUID().toString().take(8)
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
