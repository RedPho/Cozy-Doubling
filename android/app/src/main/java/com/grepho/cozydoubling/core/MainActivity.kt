package com.grepho.cozydoubling.core

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.components.ConnectionErrorDialog
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.core.economy.ThemeState
import com.grepho.cozydoubling.core.navigation.AppNavHost
import com.grepho.cozydoubling.core.navigation.BottomTab
import com.grepho.cozydoubling.core.navigation.Screen
import com.grepho.cozydoubling.core.navigation.navigateToBottomTab
import com.grepho.cozydoubling.core.network.ConnectionStateManager
import com.grepho.cozydoubling.core.network.ConnectivityObserver
import com.grepho.cozydoubling.core.profile.ProfileState
import com.grepho.cozydoubling.core.room.FocusNotificationAction
import com.grepho.cozydoubling.core.room.FocusSessionIntentExtras
import com.grepho.cozydoubling.core.room.shouldRestoreFocusRoomOnPipExit
import com.grepho.cozydoubling.features.room.FocusPipOverlay
import com.grepho.cozydoubling.ui.theme.BackgroundCream
import com.grepho.cozydoubling.ui.theme.CozyDoublingTheme
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var isInPipMode by mutableStateOf(false)
    private var returnToFocusRoomTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ServiceLocator and LocalTaskDataSource are initialised in CozyDoublingApplication.
        handleIntentExtras(intent)

        ConnectivityObserver(this).isOnline
            .onEach { ConnectionStateManager.updateConnectivity(it) }
            .launchIn(lifecycleScope)

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val themeState by ServiceLocator.economyRepository.themeState.collectAsState()
            val connectionState by ConnectionStateManager.state.collectAsState()
            val profileState by ServiceLocator.profileRepository.profileState.collectAsState()
            val authStatus by ServiceLocator.authRepository.authStatus.collectAsState()
            val activeFocusSession by ServiceLocator.focusSessionController.activeSession.collectAsState()
            val isPipEnabled by ServiceLocator.focusSessionController.isPipEnabled.collectAsState()

            var hasInitialized by rememberSaveable { mutableStateOf(false) }

            // Notification permission request
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    ServiceLocator.notificationRepository.syncToken()
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            // Sync token when authenticated
            LaunchedEffect(authStatus) {
                if (authStatus is AuthStatus.Authenticated) {
                    ServiceLocator.notificationRepository.syncToken()
                }
            }

            // Re-attempt session restore automatically if connectivity returns while initializing
            LaunchedEffect(connectionState, authStatus) {
                if (connectionState == ConnectionStateManager.ConnectionState.Available &&
                    authStatus is AuthStatus.Initializing
                ) {
                    ServiceLocator.authRepository.restoreSession()
                }
            }

            // Keep Android 12+ auto-PiP state synchronized with active focus session
            LaunchedEffect(activeFocusSession != null, isPipEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .setAutoEnterEnabled(activeFocusSession != null && isPipEnabled)
                                .build()
                            setPictureInPictureParams(params)
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            // 1. Hold on a Splash screen while Loading
            val isAuthLoading = profileState is ProfileState.Loading || authStatus is AuthStatus.Initializing
            val isThemeLoading = themeState is ThemeState.Loading

            val shouldShowSplash = !hasInitialized && (isThemeLoading || isAuthLoading) && 
                connectionState == ConnectionStateManager.ConnectionState.Available

            if (!isThemeLoading && !isAuthLoading) {
                hasInitialized = true
            }

            if (shouldShowSplash) {
                Box(modifier = Modifier.fillMaxSize().background(BackgroundCream))
            } else {
                val customPalette = (themeState as? ThemeState.Custom)?.palette

                CozyDoublingTheme(customPalette = customPalette) {
                    val snapshot = activeFocusSession
                    // Always keep CozyDoublingApp composed so entering/exiting PiP never
                    // destroys the NavController backstack or FocusRoomViewModel.
                    Box(modifier = Modifier.fillMaxSize()) {
                        CozyDoublingApp(
                            authStatus = authStatus,
                            returnToFocusRoomTrigger = returnToFocusRoomTrigger,
                            onEnterPipClick = if (isPipSupported() && isPipEnabled) {
                                {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        enterFocusPipMode()
                                    }
                                }
                            } else null
                        )

                        if (isInPipMode && snapshot != null) {
                            FocusPipOverlay(snapshot = snapshot)
                        } else {
                            ConnectionErrorDialog(
                                state = connectionState,
                                onRetry = {
                                    ConnectionStateManager.retry()
                                    if (authStatus is AuthStatus.Initializing) {
                                        scope.launch {
                                            ServiceLocator.authRepository.restoreSession()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val wasInPip = isInPipMode
        isInPipMode = isInPictureInPictureMode
        if (shouldRestoreFocusRoomOnPipExit(
                wasInPip = wasInPip,
                isNowInPip = isInPictureInPictureMode,
                isActivityStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                hasActiveSession = ServiceLocator.focusSessionController.activeSession.value != null
            )
        ) {
            returnToFocusRoomTrigger += 1
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ServiceLocator.focusSessionController.activeSession.value != null &&
            ServiceLocator.focusSessionController.isPipEnabled.value
        ) {
            enterFocusPipMode()
        }
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent == null) return
        val shouldReturn = intent.getBooleanExtra(FocusSessionIntentExtras.EXTRA_RETURN_TO_FOCUS_ROOM, false)
        val shouldFinish = intent.getBooleanExtra(FocusSessionIntentExtras.EXTRA_FINISH_SESSION, false)
        val targetSessionId = intent.getStringExtra(FocusSessionIntentExtras.EXTRA_SESSION_ID).orEmpty()
        intent.removeExtra(FocusSessionIntentExtras.EXTRA_RETURN_TO_FOCUS_ROOM)
        intent.removeExtra(FocusSessionIntentExtras.EXTRA_FINISH_SESSION)
        intent.removeExtra(FocusSessionIntentExtras.EXTRA_SESSION_ID)

        val activeSessionId = ServiceLocator.focusSessionController.activeSession.value?.sessionId ?: return
        if (targetSessionId.isNotBlank() && targetSessionId != activeSessionId) return

        if (shouldReturn) {
            returnToFocusRoomTrigger += 1
        }
        if (shouldFinish) {
            ServiceLocator.focusSessionController.emitNotificationAction(
                FocusNotificationAction.FinishSession(activeSessionId)
            )
        }
    }

    private fun isPipSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterFocusPipMode() {
        if (!isPipSupported()) return
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun CozyDoublingApp(
    authStatus: AuthStatus,
    returnToFocusRoomTrigger: Int = 0,
    onEnterPipClick: (() -> Unit)? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val isBottomTab = BottomTab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isBottomTab) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    BottomTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route

                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToBottomTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            authStatus = authStatus,
            returnToFocusRoomTrigger = returnToFocusRoomTrigger,
            onEnterPipClick = onEnterPipClick,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        )
    }
}
