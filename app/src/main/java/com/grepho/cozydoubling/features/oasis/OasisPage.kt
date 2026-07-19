package com.grepho.cozydoubling.features.oasis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- 1. Oasis Hub Title ---
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Oasis Hub",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            fontWeight = FontWeight.Bold
        )

        // --- 2. Sub-Tabs ---
        SecondaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            divider = {}, // Remove the default divider for a cleaner look
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
                    onClick = { selectedTab = tab },
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

        // --- 3. Content Area ---
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