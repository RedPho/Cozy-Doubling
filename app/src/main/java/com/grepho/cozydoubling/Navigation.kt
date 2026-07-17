package com.grepho.cozydoubling

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Oasis : Screen("oasis")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Friends : Screen("friends")
    object FocusRoom : Screen("focus_room")
}

// Now only 2 tabs!
enum class BottomTab(val route: String, val label: String, val icon: Int) {
    HOME(Screen.Home.route, "Home", R.drawable.ic_home),
    OASIS(Screen.Oasis.route, "Oasis", R.drawable.ic_home) // Replace with a shop/oasis icon
}

fun NavController.navigateToBottomTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}