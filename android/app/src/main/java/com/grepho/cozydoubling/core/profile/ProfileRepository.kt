package com.grepho.cozydoubling.core.profile

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(val profile: Profile?) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

interface ProfileRepository {
    val profileState: StateFlow<ProfileState>
    val profile: StateFlow<Profile?>
    val syncEvents: SharedFlow<Unit>

    suspend fun refreshProfile()
    suspend fun updateDisplayName(newName: String)
    suspend fun signOut()
    suspend fun syncRestoredPurchases()
    suspend fun deleteAccount()

    suspend fun setSupporter(isSupporter: Boolean)
    suspend fun updateLeaves(delta: Long)
    suspend fun updateFocusMinutes(minutes: Long)
    suspend fun setEquippedTheme(themeId: String?)
}
