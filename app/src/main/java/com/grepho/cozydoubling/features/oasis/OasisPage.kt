package com.grepho.cozydoubling.features.oasis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
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
        onTabSelected = { selectedTab = it }
    )
}

@Composable
fun OasisPage(
    topBar: @Composable () -> Unit,
    selectedTab: OasisSubTab,
    onTabSelected: (OasisSubTab) -> Unit
) {
    Scaffold(
        topBar = topBar,
        containerColor = MaterialTheme.colorScheme.background
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    OasisSubTab.SHOP -> ShopScreen()
                    OasisSubTab.INVENTORY -> InventoryScreen()
                    OasisSubTab.JOURNEY -> JourneyScreen()
                    OasisSubTab.FRIENDS -> FriendsScreen()
                }
            }
        }
    }
}