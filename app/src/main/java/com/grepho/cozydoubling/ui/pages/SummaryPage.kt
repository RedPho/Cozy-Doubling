package com.grepho.cozydoubling.ui.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart // Replace with Leaf Icon later
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SummaryPage(
    focusedMinutes: Int = 125, // Example: 2 hours 5 mins
    tasksFinished: Int = 3,
    leavesEarned: Int = 15,
    onContinueClick: () -> Unit
) {
    // --- Animation States ---
    val animatedLeafCount = remember { Animatable(0f) }
    val iconScale = remember { Animatable(1f) }

    // Start the animation when the screen opens
    LaunchedEffect(key1 = Unit) {
        delay(300) // Small pause before animation starts so the user is ready

        // Launch the pulse animation in a parallel coroutine
        launch {
            // Keep pulsing as long as the number is still counting
            while (animatedLeafCount.isRunning) {
                iconScale.animateTo(1.3f, animationSpec = tween(200))
                iconScale.animateTo(1f, animationSpec = tween(200))
            }
            // Ensure it settles exactly at 1x scale when done
            iconScale.animateTo(1f, animationSpec = tween(200))
        }

        // Count up the leaves
        animatedLeafCount.animateTo(
            targetValue = leavesEarned.toFloat(),
            animationSpec = tween(
                durationMillis = 1500, // Takes 1.5 seconds to count up
                easing = FastOutSlowInEasing
            )
        )
    }

    // --- Time Formatting ---
    val hours = focusedMinutes / 60
    val minutes = focusedMinutes % 60
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                    text = "Tasks finished: $tasksFinished",
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