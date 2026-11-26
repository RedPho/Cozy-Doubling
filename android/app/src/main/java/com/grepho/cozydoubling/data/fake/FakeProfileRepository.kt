package com.grepho.cozydoubling.data.fake

import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.core.profile.ProfileState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FakeProfileRepository(
    private val authRepository: AuthRepository? = null,
    initialProfile: Profile? = createDefaultProfile("user_cozy_001", "Cozy Doubler", "COZY1234")
) : ProfileRepository {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val userProfiles = mutableMapOf<String, Profile>()

    private val _profile = MutableStateFlow<Profile?>(initialProfile)
    override val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _profileState = MutableStateFlow<ProfileState>(
        if (initialProfile != null) ProfileState.Success(initialProfile) else ProfileState.Loading
    )
    override val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    private val _syncEvents = MutableSharedFlow<Unit>(replay = 0)
    override val syncEvents: SharedFlow<Unit> = _syncEvents.asSharedFlow()

    init {
        initialProfile?.let { userProfiles[it.id] = it }

        authRepository?.let { auth ->
            // Synchronously inspect initial auth status
            when (val initialStatus = auth.authStatus.value) {
                is AuthStatus.Authenticated -> {
                    if (_profile.value == null || _profile.value?.id != initialStatus.userId) {
                        val loaded = loadOrCreateProfile(initialStatus.userId)
                        updateProfileState(loaded)
                    }
                }
                is AuthStatus.NotAuthenticated -> {
                    updateProfileState(null)
                }
                AuthStatus.Initializing -> { /* no-op */ }
            }

            scope.launch {
                auth.authStatus.collect { status ->
                    when (status) {
                        is AuthStatus.Authenticated -> {
                            val current = _profile.value
                            if (current == null || current.id != status.userId) {
                                val loaded = loadOrCreateProfile(status.userId)
                                updateProfileState(loaded)
                            }
                        }
                        is AuthStatus.NotAuthenticated -> {
                            updateProfileState(null)
                        }
                        AuthStatus.Initializing -> {
                            // Keep current or loading
                        }
                    }
                }
            }
        }
    }

    private fun loadOrCreateProfile(userId: String): Profile {
        return userProfiles[userId] ?: run {
            val name = if (userId.startsWith("user_") && userId != "user_cozy_001") {
                userId.removePrefix("user_").replaceFirstChar { it.uppercase() }
            } else {
                "Cozy Doubler"
            }
            val tag = if (userId == "user_cozy_001") "COZY1234" else generatePlayerTag(name)
            createDefaultProfile(userId, name, tag).also {
                userProfiles[userId] = it
            }
        }
    }

    private fun updateProfileState(newProfile: Profile?) {
        _profile.value = newProfile
        _profileState.value = if (newProfile != null) ProfileState.Success(newProfile) else ProfileState.Success(null)
    }

    override suspend fun refreshProfile() {
        val currentUserId = authRepository?.getCurrentUserId() ?: _profile.value?.id
        if (currentUserId != null) {
            val loaded = loadOrCreateProfile(currentUserId)
            updateProfileState(loaded)
        }
        _syncEvents.emit(Unit)
    }

    override suspend fun updateDisplayName(newName: String) {
        val current = _profile.value ?: return
        val updated = current.copy(displayName = newName)
        userProfiles[updated.id] = updated
        updateProfileState(updated)
        _syncEvents.emit(Unit)
    }

    override suspend fun signOut() {
        updateProfileState(null)
        authRepository?.signOut()
        _syncEvents.emit(Unit)
    }

    override suspend fun syncRestoredPurchases() {
        _syncEvents.emit(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val currentId = _profile.value?.id
        if (currentId != null) {
            userProfiles.remove(currentId)
        }
        updateProfileState(null)
        authRepository?.signOut()
        _syncEvents.emit(Unit)
        return Result.success(Unit)
    }

    // Test/offline helpers that simulate server-side effects; not part of ProfileRepository.
    suspend fun setSupporter(isSupporter: Boolean) {
        val current = _profile.value ?: return
        val updated = current.copy(isSupporter = isSupporter)
        userProfiles[updated.id] = updated
        updateProfileState(updated)
        _syncEvents.emit(Unit)
    }

    suspend fun updateLeaves(delta: Long) {
        val current = _profile.value ?: return
        val updatedLeaves = maxOf(0L, current.leaves + delta)
        val updated = current.copy(leaves = updatedLeaves)
        userProfiles[updated.id] = updated
        updateProfileState(updated)
        _syncEvents.emit(Unit)
    }

    suspend fun updateFocusMinutes(minutes: Long) {
        val current = _profile.value ?: return
        val updated = current.copy(totalMinutesFocused = current.totalMinutesFocused + minutes)
        userProfiles[updated.id] = updated
        updateProfileState(updated)
        _syncEvents.emit(Unit)
    }

    override suspend fun setEquippedTheme(themeId: String?) {
        val current = _profile.value ?: return
        val updated = current.copy(equippedThemeId = themeId)
        userProfiles[updated.id] = updated
        updateProfileState(updated)
        _syncEvents.emit(Unit)
    }

    companion object {
        fun createDefaultProfile(id: String, displayName: String, playerTag: String): Profile {
            return Profile(
                id = id,
                displayName = displayName,
                playerTag = playerTag,
                equippedThemeId = null,
                leaves = 150L,
                createdAt = "2026-01-15T10:00:00Z",
                totalMinutesFocused = 120L,
                isSupporter = false,
                fcmToken = "mock_fcm_token_123"
            )
        }

        fun generatePlayerTag(seed: String): String {
            val charset = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            val clean = seed.filter { it.uppercaseChar() in charset }.uppercase()
            val prefix = if (clean.length >= 4) clean.take(4) else (clean + "COZY").take(4)
            val randomPart = (prefix.length until 8).map { charset.random() }.joinToString("")
            return prefix + randomPart
        }
    }
}
