package com.grepho.cozydoubling.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// 1. Define the enum for your tabs
enum class OasisSubTab(val title: String) {
    SHOP("Shop"),
    INVENTORY("Inventory"),
    JOURNEY("Journey"),
    FRIENDS("Friends")

}

@Composable
fun OasisPage() {
    // 2. Track the enum state instead of an integer
    var selectedTab by remember { mutableStateOf(OasisSubTab.SHOP) }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- TOP HALF: Isometric Room (future, i can't do anything art related at first, we will only use custom themes for iap and leave spends)---
        /*Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "2D Isometric Room\n(Pixel Art Goes Here)",
                color = Color.White
            )
        }*/

        // --- Shop / Journey Tabs ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 3. Use the enum's .ordinal property for the TabRow index
            SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                OasisSubTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            // 4. Clean, type-safe exhaustive when statement
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val mockFriends = listOf(
                    FriendUiState(
                        name = "Alex",
                        isOnline = true,
                        lastActiveText = "Focusing",
                        lastTask = "Writing email drafts",
                        totalLeaves = 450
                    ),
                    FriendUiState(
                        name = "Sam",
                        isOnline = false,
                        lastActiveText = "Resting",
                        lastTask = "Organized the desk",
                        totalLeaves = 1200
                    ),
                    FriendUiState(
                        name = "Jamie",
                        isOnline = true,
                        lastActiveText = "Focusing",
                        lastTask = "Reading chapter 4",
                        totalLeaves = 85
                    ),
                    FriendUiState(
                        name = "Jamie",
                        isOnline = true,
                        lastActiveText = "Focusing",
                        lastTask = "Reading chapter 4",
                        totalLeaves = 85
                    ),
                    FriendUiState(
                        name = "Jamie",
                        isOnline = true,
                        lastActiveText = "Focusing",
                        lastTask = "Reading chapter 4",
                        totalLeaves = 85
                    )
                )

                val mockProfileStats = ProfileUiState(
                    username = "CozyPanda",
                    bio = "Just here to get things done slowly.",
                    totalLeaves = 1450,
                    totalFocusHours = 42,
                    favoriteRooms = listOf("Quiet Library", "Lofi Beats Lounge", "Morning Coffee Club")
                )

                val mockUserState = UserMonetizationState(isSupporter = false, hasCozyPass = false)

                val mockThemes = listOf(
                    ThemeItemUiState("1", "Matcha Green", Color(0xFFC5E1A5), 1000, "$0.99", isPremium = false, isOwned = true, isEquipped = true),
                    ThemeItemUiState("2", "Midnight Blue", Color(0xFF1A237E), 3000, "$1.99", isPremium = true, isOwned = false),
                    ThemeItemUiState("3", "Sunset Glow", Color(0xFFFFCC80), 3000, "$1.99", isPremium = true, isOwned = false)
                )

                when (selectedTab) {
                    OasisSubTab.SHOP -> ShopPage(themes = mockThemes, userState = mockUserState)
                    OasisSubTab.INVENTORY -> InventoryPage(ownedThemes = mockThemes.filter { it.isOwned })
                    OasisSubTab.JOURNEY -> JourneyPage(mockProfileStats)
                    OasisSubTab.FRIENDS -> FriendsPage(mockFriends)
                }
            }
        }
    }
}