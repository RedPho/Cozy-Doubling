package com.grepho.cozydoubling.features.auth

import androidx.lifecycle.ViewModel
import com.grepho.cozydoubling.core.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow

class LoginViewModel : ViewModel() {
    // This allows us to observe whether the user is logged in,
    // logged out, or if the session is still loading.
    val sessionStatus: Flow<SessionStatus> = Supabase.client.auth.sessionStatus
}