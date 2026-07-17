package com.grepho.cozydoubling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grepho.cozydoubling.ui.components.CozyTopBar
import com.grepho.cozydoubling.ui.pages.HomePage
import com.grepho.cozydoubling.ui.theme.CozyDoublingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CozyDoublingTheme {
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
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomePage(
                topBar = {
                    CozyTopBar(
                        appName = "Cosy Doubling",
                        currencyCount = 200,
                        onShopClick = { navController.navigate(Screen.Shop.route) },
                        onProfileClick = { navController.navigateToBottomTab(Screen.Profile.route) },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) },
                        onFriendsClick = { navController.navigate(Screen.Friends.route) }
                    )
                }
            )
        }

        composable(Screen.Favorites.route) { Text("Favorites Page") }
        composable(Screen.Profile.route) { Text("Profile Page") }

        // Fullscreen pages
        composable(Screen.Shop.route) { Text("Shop Page - No Bottom Bar!") }
        composable(Screen.Settings.route) { Text("Settings Page") }
        composable(Screen.Friends.route) { Text("Friends Page") }
    }
}