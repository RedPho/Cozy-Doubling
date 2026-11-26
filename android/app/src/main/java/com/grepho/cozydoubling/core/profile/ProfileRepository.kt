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
    /**
     * Permanently deletes the account on the server, then clears the local session.
     * On failure nothing local is cleared, so the user is never told a still-existing account is gone.
     */
    suspend fun deleteAccount(): Result<Unit>

    // Leaves, focus minutes and supporter status are server-owned ([ECON-02], [SHOP-03]); the
    // domain interface deliberately offers no way for the client to write them.
    suspend fun setEquippedTheme(themeId: String?)
}
