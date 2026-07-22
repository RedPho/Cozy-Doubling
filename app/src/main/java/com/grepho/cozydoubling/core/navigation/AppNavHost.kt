package com.grepho.cozydoubling.core.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.grepho.cozydoubling.core.profile.ProfileRepository
import com.grepho.cozydoubling.features.auth.LoginScreen
import com.grepho.cozydoubling.features.auth.LoginViewModel
import com.grepho.cozydoubling.features.home.HomeScreen
import com.grepho.cozydoubling.features.oasis.OasisScreen
import com.grepho.cozydoubling.features.room.FocusRoomScreen
import com.grepho.cozydoubling.features.settings.SettingsScreen
import com.grepho.cozydoubling.features.summary.SummaryScreen
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppNavHost(
    navController: NavHostController,
    sessionStatus: SessionStatus,
    modifier: Modifier = Modifier,
    authViewModel: LoginViewModel = viewModel()
) {

    LaunchedEffect(Unit) {
        authViewModel.sessionStatus.collectLatest { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    // ONLY navigate to Home if we are currently stuck on the Login screen.
                    if (navController.currentDestination?.route == Screen.Login.route) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                }
                is SessionStatus.NotAuthenticated -> {
                    // User is logged out! Send them to Login
                    // We add a small delay to avoid flickering during app resume
                    delay(500)
                    if (navController.currentDestination?.route != Screen.Login.route) {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0)
                        }
                    }
                }
                else -> { /* Loading... we can just wait or show a splash */ }
            }
        }
    }

    // 🚀 NEW: Refresh profile on every screen change
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { _ ->
            ProfileRepository.refreshProfile()
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (sessionStatus is SessionStatus.Authenticated) Screen.Home.route else Screen.Login.route,
        modifier = modifier,
        enterTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            
            val isMainTabSwitch = initialRoute in listOf(Screen.Home.route, Screen.Oasis.route) &&
                                 targetRoute in listOf(Screen.Home.route, Screen.Oasis.route)
            
            if (isMainTabSwitch) {
                val isForward = initialRoute == Screen.Home.route && targetRoute == Screen.Oasis.route
                slideInHorizontally(
                    initialOffsetX = { if (isForward) 300 else -300 },
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(400))
            } else {
                fadeIn(animationSpec = tween(400))
            }
        },
        exitTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            
            val isMainTabSwitch = initialRoute in listOf(Screen.Home.route, Screen.Oasis.route) &&
                                 targetRoute in listOf(Screen.Home.route, Screen.Oasis.route)
            
            if (isMainTabSwitch) {
                val isForward = initialRoute == Screen.Home.route && targetRoute == Screen.Oasis.route
                slideOutHorizontally(
                    targetOffsetX = { if (isForward) -300 else 300 },
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(400))
            } else {
                fadeOut(animationSpec = tween(400))
            }
        },
        popEnterTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            
            val isMainTabSwitch = initialRoute in listOf(Screen.Home.route, Screen.Oasis.route) &&
                                 targetRoute in listOf(Screen.Home.route, Screen.Oasis.route)
            
            if (isMainTabSwitch) {
                val isForward = initialRoute == Screen.Home.route && targetRoute == Screen.Oasis.route
                slideInHorizontally(
                    initialOffsetX = { if (isForward) 300 else -300 },
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(400))
            } else {
                fadeIn(animationSpec = tween(400))
            }
        },
        popExitTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            
            val isMainTabSwitch = initialRoute in listOf(Screen.Home.route, Screen.Oasis.route) &&
                                 targetRoute in listOf(Screen.Home.route, Screen.Oasis.route)
            
            if (isMainTabSwitch) {
                val isForward = initialRoute == Screen.Home.route && targetRoute == Screen.Oasis.route
                slideOutHorizontally(
                    targetOffsetX = { if (isForward) -300 else 300 },
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(400))
            } else {
                fadeOut(animationSpec = tween(400))
            }
        }
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
            OasisScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBackClick = {navController.popBackStack()})
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