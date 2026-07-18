package com.grepho.cozydoubling.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.core.components.CozyTopBar

// --- THE SCREEN ENTRY POINT ---
@Composable
fun HomeScreen(
    onNavigateToFocus: () -> Unit,
    onNavigateToShop: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    // 1. Collect the profile state from the ViewModel
    // We use a default value while it's loading
    val profile by viewModel.profile.collectAsState()

    HomePage(
        topBar = {
            CozyTopBar(
                appName = "Cozy Doubling",
                // 2. Use the real leaves count!
                currencyCount = profile?.leaves?.toInt() ?: 0,
                onShopClick = onNavigateToShop,
                onSettingsClick = onNavigateToSettings
            )
        },
        onFocusClick = onNavigateToFocus,
        // 3. Pass the player tag so we can display it
        playerTag = profile?.playerTag ?: ""
    )
}

// --- THE UI COMPONENT ---
@Composable
fun HomePage(
    topBar: @Composable () -> Unit,
    onFocusClick: () -> Unit,
    playerTag: String
) {
    Scaffold(
        topBar = topBar
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Display the Supercell-style tag
            if (playerTag.isNotEmpty()) {
                Text(
                    text = "#$playerTag",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onFocusClick,
                modifier = Modifier.size(200.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    text = "FOCUS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}