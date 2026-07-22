package com.grepho.cozydoubling.features.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    // 1. Map the real profile into the Settings UI State
    val uiState: StateFlow<SettingsUiState> = ProfileRepository.profile
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
                ProfileRepository.updateDisplayName(newName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            ProfileRepository.signOut()
        }
    }

    fun onDeleteAccount() {
        viewModelScope.launch {
            try {
                ProfileRepository.deleteAccount()
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
            val success = com.grepho.cozydoubling.core.billing.BillingRepository.restorePurchases()
            if (success) {
                // Force backend to sync with RevenueCat
                ProfileRepository.triggerBackendRestoreSync()
                
                // Optional: Short retry loop in case the backend process takes a moment
                repeat(2) {
                    kotlinx.coroutines.delay(1000)
                    ProfileRepository.refreshProfile()
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

