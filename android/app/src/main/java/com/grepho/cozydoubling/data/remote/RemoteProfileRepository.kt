package com.grepho.cozydoubling.data.remote

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.profile.ProfileState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class RemoteProfileRepository(
    private val apiClient: ApiClient,
    private val authRepository: AuthRepository
) : ProfileRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    override val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    override val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _syncEvents = MutableSharedFlow<Unit>(replay = 0)
    override val syncEvents: SharedFlow<Unit> = _syncEvents.asSharedFlow()

    init {
        scope.launch {
            authRepository.authStatus.collect { status ->
                when (status) {
                    is AuthStatus.Authenticated -> {
                        refreshProfile()
                    }
                    is AuthStatus.NotAuthenticated -> {
                        _profile.value = null
                        _profileState.value = ProfileState.Success(null)
                    }
                    AuthStatus.Initializing -> {
                        _profileState.value = ProfileState.Loading
                    }
                }
            }
        }
    }

    override suspend fun refreshProfile() {
        if (apiClient.authToken == null) return
        val result = apiClient.get("/api/profile") { body ->
            apiClient.json.decodeFromString<ProfileDto>(body).toDomain()
        }
        result.fold(
            onSuccess = { fetchedProfile ->
                _profile.value = fetchedProfile
                _profileState.value = ProfileState.Success(fetchedProfile)
                _syncEvents.emit(Unit)
            },
            onFailure = { error ->
                _profileState.value = ProfileState.Error(error.message ?: "Failed to fetch profile")
            }
        )
    }

    override suspend fun updateDisplayName(newName: String) {
        val body = apiClient.json.encodeToString(UpdateProfileRequestDto(displayName = newName))
        val result = apiClient.patch("/api/profile", body) { res ->
            apiClient.json.decodeFromString<ProfileDto>(res).toDomain()
        }
        result.onSuccess {
            refreshProfile()
        }
    }

    override suspend fun signOut() {
        authRepository.signOut()
        _profile.value = null
        _profileState.value = ProfileState.Success(null)
        _syncEvents.emit(Unit)
    }

    override suspend fun syncRestoredPurchases() {
        val body = apiClient.json.encodeToString(SyncSubscriptionRequestDto(isSupporter = true))
        apiClient.post("/api/subscriptions/sync", body) { }
        refreshProfile()
    }

    override suspend fun deleteAccount() {
        apiClient.delete("/api/auth/account") { }
        authRepository.signOut()
        _profile.value = null
        _profileState.value = ProfileState.Success(null)
        _syncEvents.emit(Unit)
    }

    override suspend fun setSupporter(isSupporter: Boolean) {
        val body = apiClient.json.encodeToString(SyncSubscriptionRequestDto(isSupporter = isSupporter))
        apiClient.post("/api/subscriptions/sync", body) { }
        refreshProfile()
    }

    override suspend fun updateLeaves(delta: Long) {
        // Optimistic local update while sync settles
        val current = profile.value ?: return
        val updated = current.copy(leaves = maxOf(0L, current.leaves + delta))
        _profile.value = updated
        _profileState.value = ProfileState.Success(updated)
        _syncEvents.emit(Unit)
    }

    override suspend fun updateFocusMinutes(minutes: Long) {
        val current = profile.value ?: return
        val updated = current.copy(totalMinutesFocused = current.totalMinutesFocused + minutes)
        _profile.value = updated
        _profileState.value = ProfileState.Success(updated)
        _syncEvents.emit(Unit)
    }

    override suspend fun setEquippedTheme(themeId: String?) {
        if (themeId == null || themeId == "system_default") {
            apiClient.post("/api/inventory/unequip", null) { }
        } else {
            val body = apiClient.json.encodeToString(EquipItemRequestDto(themeId = themeId))
            apiClient.post("/api/inventory/equip", body) { }
        }
        refreshProfile()
    }
}
