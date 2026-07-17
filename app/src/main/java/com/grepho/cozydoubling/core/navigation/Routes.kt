package com.grepho.cozydoubling.core.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.grepho.cozydoubling.R

// 1. Every single destination in your app
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Oasis : Screen("oasis")
    object Settings : Screen("settings")
    object FocusRoom : Screen("focus_room")
    object Summary : Screen("summary")
}

// 2. Just the destinations that appear on the Bottom Bar
enum class BottomTab(val route: String, val label: String, val icon: Int) {
    HOME(Screen.Home.route, "Home", R.drawable.ic_home),
    OASIS(Screen.Oasis.route, "Oasis", R.drawable.ic_home) // TODO: Replace with shop/oasis icon
}

// 3. Your helper function so the tabs don't stack up infinitely when clicked
fun NavController.navigateToBottomTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}