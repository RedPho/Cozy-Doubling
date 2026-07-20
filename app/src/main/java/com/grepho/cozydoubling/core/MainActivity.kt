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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.economy.ThemeState
import com.grepho.cozydoubling.core.navigation.AppNavHost
import com.grepho.cozydoubling.core.navigation.BottomTab
import com.grepho.cozydoubling.core.navigation.Screen
import com.grepho.cozydoubling.core.navigation.navigateToBottomTab
import com.grepho.cozydoubling.ui.theme.BackgroundCream
import com.grepho.cozydoubling.ui.theme.CozyDoublingTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeState by EconomyRepository.themeState.collectAsState()

            // 1. Hold on a Splash screen while Loading
            if (themeState is ThemeState.Loading) {
                // Show a solid background matching your brand (BackgroundCream)
                Box(modifier = Modifier.fillMaxSize().background(BackgroundCream))
            } else {
                // 2. Resolve the palette (Custom or Default)
                val customPalette = (themeState as? ThemeState.Custom)?.palette

                CozyDoublingTheme(customPalette = customPalette) {
                    CozyDoublingApp()
                }
            }
        }
    }
}

@Composable
fun CozyDoublingApp() {
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
                                    painter = painterResource(tab.icon),
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
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        )
    }
}

