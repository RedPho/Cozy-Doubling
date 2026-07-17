package com.grepho.cozydoubling.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.grepho.cozydoubling.ui.components.CozyTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage() {
    Scaffold (
        topBar = {
            CozyTopBar(
                appName = "Cosy Doubling",
                currencyCount = 200,
                onShopClick = {}, //navigate to oasis
                onProfileClick = {}, //navigate to profile
                onSettingsClick = {}, //navigate to settings
                onFriendsClick = {} //navigate to friends
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {

        }
    }
}

@Preview
@Composable
fun homepagerenderprew() {
    HomePage();
}