package com.grepho.cozydoubling.features.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart // Replace with Leaf Icon later
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- THE SCREEN ENTRY POINT ---
@Composable
fun SummaryScreen(
    sessionId: String,
    onContinueClick: () -> Unit,
    viewModel: SummaryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSessionStats(sessionId)
    }

    SummaryPage(
        uiState = uiState,
        onContinueClick = onContinueClick
    )
}

// --- THE UI COMPONENT ---
@Composable
fun SummaryPage(
    uiState: SummaryUiState,
    onContinueClick: () -> Unit
) {
    // --- Animation States ---
    val animatedLeafCount = remember { Animatable(0f) }
    val iconScale = remember { Animatable(1f) }

    // Start the animation when the screen opens
    LaunchedEffect(uiState.leavesEarned) {
        // 1. Safety check: Don't animate if we don't have data yet
        if (uiState.leavesEarned == 0) return@LaunchedEffect

        delay(300) // Small breather for the user

        // 2. We capture the 'counting' animation in a variable
        val countJob = launch {
            animatedLeafCount.animateTo(
                targetValue = uiState.leavesEarned.toFloat(),
                animationSpec = tween(
                    durationMillis = 1000, // Slightly faster (1s) feels snappier
                    easing = FastOutSlowInEasing
                )
            )
        }

        // 3. Pulse only while the counting is actually happening
        launch {
            while (countJob.isActive) {
                iconScale.animateTo(1.3f, animationSpec = tween(150))
                iconScale.animateTo(1f, animationSpec = tween(150))
            }
            // Final settle to make sure it's exactly 1x
            iconScale.animateTo(1f, animationSpec = tween(100))
        }
    }

    // --- Time Formatting ---
    val hours = uiState.focusedMinutes / 60
    val minutes = uiState.focusedMinutes % 60
    val timeString = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Great job showing up today!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // --- Stats Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Time focused: $timeString",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Tasks finished: ${uiState.tasksFinished}",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "You gathered:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Animated Reward Section ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "+${animatedLeafCount.value.toInt()}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        imageVector = Icons.Default.ShoppingCart, // Replace with your Leaf icon
                        contentDescription = "Leaves",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(36.dp)
                            .scale(iconScale.value) // Applies the pulsing scale
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // --- Finish Button ---
        Button(
            onClick = onContinueClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Continue", style = MaterialTheme.typography.titleMedium)
        }
    }
}