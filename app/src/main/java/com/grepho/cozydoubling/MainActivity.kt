package com.grepho.cozydoubling

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
import com.grepho.cozydoubling.ui.pages.OasisPage
import com.grepho.cozydoubling.ui.pages.SettingsPage
import com.grepho.cozydoubling.ui.theme.CozyDoublingTheme
import com.grepho.cozydoubling.ui.pages.FocusRoomPage
import com.grepho.cozydoubling.ui.pages.SummaryPage

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
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
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
        // HOME TAB
        composable(Screen.Home.route) {
            HomePage(
                topBar = {
                    CozyTopBar(
                        appName = "Cosy Doubling",
                        currencyCount = 1450,
                        onShopClick = {
                            navController.navigateToBottomTab(Screen.Oasis.route)
                        },
                        onSettingsClick = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                },
                onFocusClick = {
                    navController.navigate(Screen.FocusRoom.route)
                }
            )
        }

        // OASIS TAB
        composable(Screen.Oasis.route) {
            OasisPage()
        }

        // OTHER PAGES
        composable(Screen.Settings.route) { SettingsPage() }
        composable(Screen.Friends.route) { Text("Friends Page") }

        // FOCUS ROOM (No bottom bar or top bar by default, totally immersive)
        composable(Screen.FocusRoom.route) {
            FocusRoomPage(
                onLeaveClick = {
                    navController.navigate(Screen.Summary.route) {
                        // This prevents them from hitting the back button to re-enter the room
                        popUpTo(Screen.FocusRoom.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Summary.route) {
            SummaryPage(
                onContinueClick = {
                    // Go back to the Home screen and clear the summary off the stack
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}