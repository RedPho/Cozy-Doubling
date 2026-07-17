package com.grepho.cozydoubling.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    // TODO: We will replace this with a state collected from the HomeViewModel later!
    val mockCurrencyCount = 1450

    HomePage(
        topBar = {
            CozyTopBar(
                appName = "Cosy Doubling",
                currencyCount = mockCurrencyCount,
                onShopClick = onNavigateToShop,
                onSettingsClick = onNavigateToSettings
            )
        },
        onFocusClick = onNavigateToFocus
    )
}

// --- THE UI COMPONENT ---
@Composable
fun HomePage(
    topBar: @Composable () -> Unit,
    onFocusClick: () -> Unit
) {
    Scaffold(
        topBar = topBar
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
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