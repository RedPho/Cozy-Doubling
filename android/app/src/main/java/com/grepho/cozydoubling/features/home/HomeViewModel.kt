package com.grepho.cozydoubling.features.home

import androidx.lifecycle.ViewModel
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.profile.ProfileRepository

class HomeViewModel(
    profileRepository: ProfileRepository = ServiceLocator.profileRepository
) : ViewModel() {

    // We expose the StateFlow from the repository directly to the UI
    val profile = profileRepository.profile
}
