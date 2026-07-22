package com.grepho.cozydoubling.features.oasis

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.core.components.CozyTopBar

// Import the Screens instead of the individual UiStates!
import com.grepho.cozydoubling.features.friends.FriendsScreen
import com.grepho.cozydoubling.features.home.HomeViewModel
import com.grepho.cozydoubling.features.inventory.InventoryScreen
import com.grepho.cozydoubling.features.journey.JourneyScreen
import com.grepho.cozydoubling.features.shop.ShopScreen

enum class OasisSubTab(val title: String) {
    SHOP("Shop"),
    INVENTORY("Inventory"),
    JOURNEY("Journey"),
    FRIENDS("Friends")
}


@Composable
fun OasisScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel() // Reusing HomeViewModel for profile data
) {
    val profile by viewModel.profile.collectAsState()
    var selectedTab by remember { mutableStateOf(OasisSubTab.SHOP) }
    val snackbarHostState = remember { SnackbarHostState() }

    OasisPage(
        topBar = {
            CozyTopBar(
                appName = "Cozy Doubling",
                currencyCount = profile?.leaves?.toInt() ?: 0,
                onShopClick = { selectedTab = OasisSubTab.SHOP }, // Switch to shop tab
                onSettingsClick = onNavigateToSettings
            )
        },
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun OasisPage(
    topBar: @Composable () -> Unit,
    selectedTab: OasisSubTab,
    onTabSelected: (OasisSubTab) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        topBar = topBar,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // REMOVED: The "Oasis Hub" title block is gone!

            // --- 1. Sub-Tabs ---
            SecondaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                divider = {},
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            ) {
                OasisSubTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    Tab(
                        selected = selected,
                        onClick = { onTabSelected(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            // --- 2. Content Area ---
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally { width -> width / 4 } + fadeIn())
                            .togetherWith(slideOutHorizontally { width -> -width / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width / 4 } + fadeIn())
                            .togetherWith(slideOutHorizontally { width -> width / 4 } + fadeOut())
                    }
                },
                label = "OasisTabTransition"
            ) { tab ->
                when (tab) {
                    OasisSubTab.SHOP -> ShopScreen()
                    OasisSubTab.INVENTORY -> InventoryScreen()
                    OasisSubTab.JOURNEY -> JourneyScreen()
                    OasisSubTab.FRIENDS -> FriendsScreen(snackbarHostState = snackbarHostState)
                }
            }
        }
    }
}
