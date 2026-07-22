package com.grepho.cozydoubling.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

// 1. Every single destination in your app
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Oasis : Screen("oasis")
    object Settings : Screen("settings")
    object FocusRoom : Screen("focus_room")
    object Summary : Screen("summary/{sessionId}") {
        fun createRoute(sessionId: String) = "summary/$sessionId"
    }
    object Login : Screen("login")
}

// 2. Just the destinations that appear on the Bottom Bar
enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    HOME(Screen.Home.route, "Home", Icons.Rounded.Home),
    OASIS(Screen.Oasis.route, "Oasis", Icons.Rounded.Spa)
}

// 3. Your helper function so the tabs don't stack up infinitely when clicked
fun NavController.navigateToBottomTab(route: String) {
    if (currentDestination?.route == route) return

    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
