package com.grepho.cozydoubling.features.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

// --- THE SCREEN ENTRY POINT ---
@Composable
fun JourneyScreen(
    modifier: Modifier = Modifier,
    viewModel: JourneyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    JourneyPage(
        uiState = uiState,
        modifier = modifier
    )
}

// --- THE UI COMPONENT ---
@Composable
fun JourneyPage(
    uiState: ProfileUiState,
    modifier: Modifier = Modifier
) {
    val totalMinutes = uiState.totalFocusMinutes
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val timeString = if (hours > 0) "${hours}h" else "${minutes}m" // Matching design style

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // --- 1. Avatar Section ---
        Surface(
            modifier = Modifier.size(140.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Background image/gradient circle
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.9f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                )
                // Existing initial avatar logic
                Text(
                    text = uiState.username.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "${uiState.username}'s Oasis",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

//        Text(
//            text = "Cozy Doubler since July 2026", // You can format uiState.joinDate here
//            style = MaterialTheme.typography.bodyMedium,
//            color = MaterialTheme.colorScheme.onSurfaceVariant
//        )
//
//        Spacer(modifier = Modifier.height(40.dp))

        // --- 2. Stats Cards (Vertical Stack) ---
        JourneyStatCard(
            title = "Total Leaves",
            value = String.format("%,d", uiState.totalLeaves),
            icon = Icons.Default.Eco,
            iconBgColor = MaterialTheme.colorScheme.tertiaryContainer
        )

        Spacer(modifier = Modifier.height(16.dp))

        JourneyStatCard(
            title = "Time in Deep Focus",
            value = timeString,
            icon = Icons.Default.AccessTime,
            iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun JourneyStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconBgColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        }
    }
}

// --- Mock Preview ---
@Preview(showBackground = true)
@Composable
fun ProfilePagePreview() {
    MaterialTheme {
        JourneyPage(
            uiState = ProfileUiState(
                username = "CozyPanda",
                bio = "Just here to get things done slowly.",
                totalLeaves = 1450,
                totalFocusMinutes = 124
            )
        )
    }
}