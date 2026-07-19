package com.grepho.cozydoubling.core

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grepho.cozydoubling.core.economy.EconomyRepository
import com.grepho.cozydoubling.core.navigation.AppNavHost
import com.grepho.cozydoubling.core.navigation.BottomTab
import com.grepho.cozydoubling.core.navigation.Screen
import com.grepho.cozydoubling.core.navigation.navigateToBottomTab
import com.grepho.cozydoubling.ui.theme.CozyDoublingTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val activePalette by EconomyRepository.activePalette.collectAsState()

            CozyDoublingTheme(customPalette = activePalette) {
                CozyDoublingApp()
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
                NavigationBar {
                    BottomTab.entries.forEach { tab ->
                        NavigationBarItem(
                            icon = { Icon(painterResource(tab.icon), contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateToBottomTab(tab.route) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
        )
    }
}

