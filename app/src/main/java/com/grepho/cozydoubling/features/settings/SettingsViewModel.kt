package com.grepho.cozydoubling.features.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.auth.AuthRepository
import com.grepho.cozydoubling.core.billing.BillingRepository
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.notifications.NotificationRepository
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.room.LocalTaskDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val profileRepository: ProfileRepository = ServiceLocator.profileRepository,
    private val billingRepository: BillingRepository = ServiceLocator.billingRepository,
    private val authRepository: AuthRepository = ServiceLocator.authRepository,
    private val notificationRepository: NotificationRepository = ServiceLocator.notificationRepository
) : ViewModel() {

    constructor() : this(
        profileRepository = ServiceLocator.profileRepository,
        billingRepository = ServiceLocator.billingRepository,
        authRepository = ServiceLocator.authRepository,
        notificationRepository = ServiceLocator.notificationRepository
    )

    // 1. Map the real profile into the Settings UI State
    val uiState: StateFlow<SettingsUiState> = profileRepository.profile
        .map { profile ->
            SettingsUiState(
                username = profile?.displayName ?: "Loading...",
                isSupporter = profile?.isSupporter ?: false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(username = "Loading...")
        )

    fun onUpdateUsername(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            try {
                profileRepository.updateDisplayName(newName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            try {
                notificationRepository.clearToken()
                LocalTaskDataSource.clear()
                profileRepository.signOut()
                authRepository.signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onDeleteAccount() {
        viewModelScope.launch {
            try {
                notificationRepository.clearToken()
                LocalTaskDataSource.clear()
                profileRepository.deleteAccount()
                authRepository.signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onManageSubscription(context: Context) {
        val packageName = context.packageName
        val url = "https://play.google.com/store/account/subscriptions?package=$packageName"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            setPackage("com.android.vending")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback if Play Store is not installed
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun onRestorePurchases(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val success = billingRepository.restorePurchases()
            if (success) {
                profileRepository.triggerBackendRestoreSync()
                repeat(2) {
                    delay(500)
                    profileRepository.refreshProfile()
                }
                onResult("Purchases restored successfully!")
            } else {
                onResult("No active purchases found to restore.")
            }
        }
    }

    fun onOpenUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
