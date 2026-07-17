package com.grepho.cozydoubling.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.grepho.cozydoubling.features.home.HomeScreen
import com.grepho.cozydoubling.features.oasis.OasisPage
import com.grepho.cozydoubling.features.room.FocusRoomScreen
import com.grepho.cozydoubling.features.settings.SettingsScreen
import com.grepho.cozydoubling.features.summary.SummaryScreen

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            // All routing logic is passed in here, but the TopBar is drawn inside HomeScreen!
            HomeScreen(
                onNavigateToFocus = { navController.navigate(Screen.FocusRoom.route) },
                onNavigateToShop = { navController.navigateToBottomTab(Screen.Oasis.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Oasis.route) {
            OasisPage()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(Screen.FocusRoom.route) {
            FocusRoomScreen(
                onLeaveClick = {
                    navController.navigate(Screen.Summary.route) {
                        popUpTo(Screen.FocusRoom.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Summary.route) {
            SummaryScreen(
                onContinueClick = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}