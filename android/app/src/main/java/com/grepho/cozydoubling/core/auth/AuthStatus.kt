package com.grepho.cozydoubling.core.auth

sealed class AuthStatus {
    object Initializing : AuthStatus()
    data class Authenticated(val userId: String) : AuthStatus()
    object NotAuthenticated : AuthStatus()
}
