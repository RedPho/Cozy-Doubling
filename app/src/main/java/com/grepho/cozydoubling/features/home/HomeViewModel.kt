package com.grepho.cozydoubling.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grepho.cozydoubling.core.Supabase
import com.grepho.cozydoubling.core.profile.ProfileRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repository = ProfileRepository()

    // We expose the StateFlow from the repository directly to the UI
    val profile = repository.profile

    init {
        // Fetch the data from Supabase immediately
        viewModelScope.launch {
            repository.refreshProfile()
        }
    }
}