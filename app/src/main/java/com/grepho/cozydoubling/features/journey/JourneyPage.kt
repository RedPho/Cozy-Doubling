package com.grepho.cozydoubling.features.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val timeString = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Avatar & Identity ---
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder for actual avatar image
            Text(
                text = uiState.username.take(1).uppercase(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = uiState.username,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = uiState.bio,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- 2. Gentle Stats (Cumulative Only, No Streaks) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GentleStatCard(
                title = "Leaves Gathered",
                value = uiState.totalLeaves.toString(),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            GentleStatCard(
                title = "Time Focused",
                value = timeString, // Use the formatted string
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun GentleStatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
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