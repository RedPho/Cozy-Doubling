package com.grepho.cozydoubling.features.oasis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Import the Screens instead of the individual UiStates!
import com.grepho.cozydoubling.features.friends.FriendsScreen
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
fun OasisPage() {
    var selectedTab by remember { mutableStateOf(OasisSubTab.SHOP) }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Shop / Journey Tabs ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                OasisSubTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Look how clean this is now!
                // Each Screen automatically handles its own ViewModel and State.
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