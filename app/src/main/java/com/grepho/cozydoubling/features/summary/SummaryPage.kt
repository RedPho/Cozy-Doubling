package com.grepho.cozydoubling.features.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ShoppingCart // Replace with Leaf Icon later
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header ---
        Text(
            text = "Great job showing up today!",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Your garden is growing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // --- Leaves Earned Card ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f),
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Central Graphic (Simplified orbit effect)
                Box(contentAlignment = Alignment.Center) {
                    // Outer orbit path (optional dashed circle)

                    // The main leaf circle
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = "leaf icon",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "${animatedLeafCount.value.toInt()}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "LEAVES EARNED",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Session Summary Card ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Session Summary",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Time Row
                SummaryRow(
                    label = "Time Focused",
                    value = timeString,
                    icon = Icons.Default.AccessTime
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Tasks Row
                SummaryRow(
                    label = "Tasks Finished",
                    value = "${uiState.tasksFinished} tasks",
                    icon = Icons.Default.CheckCircleOutline
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- Continue Button ---
        Button(
            onClick = onContinueClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, icon: ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}