package com.grepho.cozydoubling.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.grepho.cozydoubling.features.auth.LoginScreen
import com.grepho.cozydoubling.features.auth.LoginViewModel
import com.grepho.cozydoubling.features.home.HomeScreen
import com.grepho.cozydoubling.features.oasis.OasisPage
import com.grepho.cozydoubling.features.room.FocusRoomScreen
import com.grepho.cozydoubling.features.settings.SettingsScreen
import com.grepho.cozydoubling.features.summary.SummaryScreen
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier, authViewModel: LoginViewModel = viewModel() ) {

    LaunchedEffect(Unit) {
        authViewModel.sessionStatus.collect { status ->
            val currentRoute = navController.currentDestination?.route

            when (status) {
                is SessionStatus.Authenticated -> {
                    // ONLY navigate to Home if we are currently stuck on the Login screen
                    if (currentRoute == Screen.Login.route || currentRoute == null) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                }
                is SessionStatus.NotAuthenticated -> {
                    // User is logged out! Send them to Login
                        if (currentRoute != Screen.Login.route) {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0)
                            }
                        }
                }
                else -> { /* Loading... we can just wait or show a splash */ }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen()
        }


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
                onNavigateToSummary = { sessionId ->
                    navController.navigate(Screen.Summary.createRoute(sessionId)) {
                        popUpTo(Screen.FocusRoom.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Summary.route,
            arguments = listOf(navArgument("sessionId") {type = NavType.StringType})
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            SummaryScreen(
                sessionId,
                onContinueClick = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}