package com.grepho.cozydoubling.core.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.features.auth.LoginScreen
import com.grepho.cozydoubling.features.auth.LoginViewModel
import com.grepho.cozydoubling.features.home.HomeScreen
import com.grepho.cozydoubling.features.oasis.OasisScreen
import com.grepho.cozydoubling.features.room.FocusRoomScreen
import com.grepho.cozydoubling.features.room.InterruptedSessionRecoveryCoordinator
import com.grepho.cozydoubling.features.settings.SettingsScreen
import com.grepho.cozydoubling.features.summary.SummaryScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppNavHost(
    navController: NavHostController,
    authStatus: AuthStatus,
    returnToFocusRoomTrigger: Int = 0,
    onEnterPipClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    authViewModel: LoginViewModel = viewModel()
) {

    LaunchedEffect(Unit) {
        authViewModel.sessionStatus.collectLatest { status ->
            when (status) {
                is AuthStatus.Authenticated -> {
                    // ONLY navigate to Home if we are currently stuck on the Login screen.
                    if (navController.currentDestination?.route == Screen.Login.route) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                }
                is AuthStatus.NotAuthenticated -> {
                    // ⏳ DEBOUNCE: Short delay before redirecting to Login screen.
                    delay(150)

                    // User is definitively logged out! Send them to Login
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

    var hasAttemptedSessionRecovery by rememberSaveable { mutableStateOf(false) }

    // Detect and finalize a session interrupted by app closure once on cold start after auth
    LaunchedEffect(authStatus) {
        if (!hasAttemptedSessionRecovery && authStatus is AuthStatus.Authenticated) {
            hasAttemptedSessionRecovery = true
            val isOnFocusRoom = navController.currentDestination?.route?.startsWith(Screen.FocusRoom.BASE_ROUTE) == true
            if (!isOnFocusRoom) {
                val recovered = InterruptedSessionRecoveryCoordinator(
                    sessionController = ServiceLocator.focusSessionController,
                    roomRepository = ServiceLocator.focusRoomRepository,
                    profileRepository = ServiceLocator.profileRepository
                ).recoverIfNeeded()
                if (recovered != null) {
                    navController.navigate(
                        Screen.Summary.createRoute(recovered.sessionId, wasInterrupted = true)
                    ) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    // Bring Focus Room to front when tapped from the persistent notification
    LaunchedEffect(returnToFocusRoomTrigger, authStatus) {
        val isOnFocusRoom = navController.currentDestination?.route?.startsWith(Screen.FocusRoom.BASE_ROUTE) == true
        if (returnToFocusRoomTrigger > 0 &&
            authStatus is AuthStatus.Authenticated &&
            ServiceLocator.focusSessionController.activeSession.value != null &&
            !isOnFocusRoom
        ) {
            navController.navigate(Screen.FocusRoom.createRoute(isRecovery = true)) {
                launchSingleTop = true
            }
        }
    }

    // 🚀 NEW: Refresh profile on every screen change
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { _ ->
            ServiceLocator.profileRepository.refreshProfile()
        }
    }

    // 🛡️ STABLE NAVIGATION GRAPH
    // We remember the initial start destination to prevent the NavHost from rebuilding 
    // its entire backstack if the sessionStatus flickers to 'Initializing' or 'Loading'.
    val initialStartDestination = remember {
        if (authStatus is AuthStatus.Authenticated) Screen.Home.route else Screen.Login.route
    }

    NavHost(
        navController = navController,
        startDestination = initialStartDestination,
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
                onNavigateToFocus = {
                    val hasLiveSession = ServiceLocator.focusSessionController.activeSession.value != null
                    navController.navigate(Screen.FocusRoom.createRoute(isRecovery = hasLiveSession))
                },
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

        composable(
            route = Screen.FocusRoom.route,
            arguments = listOf(navArgument("recovery") {
                type = NavType.BoolType
                defaultValue = false
            })
        ) {
            FocusRoomScreen(
                onNavigateToSummary = { sessionId ->
                    navController.navigate(Screen.Summary.createRoute(sessionId)) {
                        popUpTo(Screen.FocusRoom.route) { inclusive = true }
                    }
                },
                onEnterPipClick = onEnterPipClick
            )
        }

        composable(
            route = Screen.Summary.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("interrupted") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val wasInterrupted = backStackEntry.arguments?.getBoolean("interrupted") ?: false
            SummaryScreen(
                sessionId = sessionId,
                wasInterrupted = wasInterrupted,
                onContinueClick = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}