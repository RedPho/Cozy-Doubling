package com.grepho.cozydoubling.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    JOURNEY("Journey")
}

@Composable
fun OasisPage() {
    // 2. Track the enum state instead of an integer
    var selectedTab by remember { mutableStateOf(OasisSubTab.SHOP) }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- TOP HALF: Isometric Room ---
        Box(
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
        }

        // --- BOTTOM HALF: Shop / Journey Tabs ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // 3. Use the enum's .ordinal property for the TabRow index
            TabRow(selectedTabIndex = selectedTab.ordinal) {
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
                when (selectedTab) {
                    OasisSubTab.SHOP -> Text("Shop Interface")
                    OasisSubTab.JOURNEY -> Text("Journey Interface")
                }
            }
        }
    }
}