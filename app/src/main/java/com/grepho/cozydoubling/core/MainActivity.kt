package com.grepho.cozydoubling.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.grepho.cozydoubling.BuildConfig
import com.grepho.cozydoubling.core.components.ConnectionErrorDialog
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.economy.ThemeState
import com.grepho.cozydoubling.core.navigation.AppNavHost
import com.grepho.cozydoubling.core.navigation.BottomTab
import com.grepho.cozydoubling.core.navigation.Screen
import com.grepho.cozydoubling.core.navigation.navigateToBottomTab
import com.grepho.cozydoubling.core.network.ConnectionStateManager
import com.grepho.cozydoubling.core.network.ConnectivityObserver
import com.grepho.cozydoubling.ui.theme.BackgroundCream
import com.grepho.cozydoubling.ui.theme.CozyDoublingTheme
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_APIKEY).build()
        )

        ConnectivityObserver(this).isOnline
            .onEach { ConnectionStateManager.updateConnectivity(it) }
            .launchIn(lifecycleScope)

        enableEdgeToEdge()
        setContent {
            val themeState by EconomyRepository.themeState.collectAsState()
            val connectionState by ConnectionStateManager.state.collectAsState()
            val sessionStatus by remember { Supabase.client.auth.sessionStatus }.collectAsState(initial = SessionStatus.Initializing)

            // 1. Hold on a Splash screen while Loading
            // We consider the session "Loading" if it's Initializing OR if it's briefly
            // NotAuthenticated during a resume (handled by AppNavHost's delay).
            val isAuthLoading = sessionStatus is SessionStatus.Initializing
            if ((themeState is ThemeState.Loading || isAuthLoading) && connectionState == ConnectionStateManager.ConnectionState.Available) {
                // Show a solid background matching your brand (BackgroundCream)
                Box(modifier = Modifier.fillMaxSize().background(BackgroundCream))
            } else {
                // 2. Resolve the palette (Custom or Default)
                val customPalette = (themeState as? ThemeState.Custom)?.palette

                CozyDoublingTheme(customPalette = customPalette) {
                    Box {
                        CozyDoublingApp(sessionStatus)
                        
                        ConnectionErrorDialog(
                            state = connectionState,
                            onRetry = { ConnectionStateManager.retry() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CozyDoublingApp(sessionStatus: SessionStatus) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val isBottomTab = BottomTab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isBottomTab) {
                NavigationBar(
                    // Transparent background so the cream shows through
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
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    // Use our new typography for the label
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                // The capsule/pill color
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                // Colors for the icon and label when selected
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                // Colors for when NOT selected
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
            sessionStatus = sessionStatus,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        )
    }
}

