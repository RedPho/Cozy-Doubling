package com.grepho.cozydoubling.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        onFocusClick = onNavigateToFocus
    )
}

// --- THE UI COMPONENT ---
@Composable
fun HomePage(
    topBar: @Composable () -> Unit,
    onFocusClick: () -> Unit,
) {

    val ringColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Scaffold(
        topBar = topBar,
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // The Central Ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(300.dp)
                // Optional: Add a subtle glow/shadow here
            ) {
                // Outer Ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = ringColor,
                        style = Stroke(width = 12.dp.toPx())
                    )
                }

                // Start Focus Capsule Button
                Button(
                    onClick = onFocusClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Focus",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Description Text
            Text(
                text = "Ready for a quiet session? Lets focus together.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}