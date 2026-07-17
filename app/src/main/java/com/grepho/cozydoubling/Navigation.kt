package com.grepho.cozydoubling
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.grepho.cozydoubling.R

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Favorites : Screen("favorites")
    object Profile : Screen("profile")
    object Shop : Screen("shop")
    object Settings : Screen("settings")
    object Friends : Screen("friends")
}

enum class BottomTab(val route: String, val label: String, val icon: Int) {
    HOME(Screen.Home.route, "Home", R.drawable.ic_home),
    FAVORITES(Screen.Favorites.route, "Favorites", R.drawable.ic_favorite),
    PROFILE(Screen.Profile.route, "Profile", R.drawable.ic_account_box)
}

fun NavController.navigateToBottomTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}