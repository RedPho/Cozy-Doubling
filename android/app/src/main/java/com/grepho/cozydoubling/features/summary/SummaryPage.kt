package com.grepho.cozydoubling.features.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart // Replace with Leaf Icon later
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- THE SCREEN ENTRY POINT ---
@Composable
fun SummaryScreen(
    sessionId: String,
    wasInterrupted: Boolean = false,
    onContinueClick: () -> Unit,
    viewModel: SummaryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId, wasInterrupted) {
        viewModel.loadSessionStats(sessionId, wasInterrupted = wasInterrupted)
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
    val timeString = if (hours > 0) {
        stringResource(R.string.summary_time_format_hours, hours, minutes)
    } else {
        stringResource(R.string.summary_time_format_minutes, minutes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (headerRes, subheaderRes) = when {
                uiState.endReason == "abandoned_disconnect" ->
                    R.string.summary_header_abandoned to R.string.summary_subheader_abandoned
                uiState.endReason == "app_closed" || uiState.wasInterrupted ->
                    R.string.summary_header_app_closed to R.string.summary_subheader_app_closed
                else ->
                    R.string.summary_header to R.string.summary_subheader
            }

            val noticeRes = when {
                uiState.loadError -> R.string.summary_notice_load_error
                uiState.endReason == "app_closed" -> R.string.summary_notice_app_closed
                uiState.endReason == "abandoned_disconnect" -> R.string.summary_notice_abandoned_disconnect
                uiState.endReason == "capped_max_duration" || uiState.wasCapped -> R.string.summary_notice_capped_max
                uiState.wasInterrupted -> R.string.summary_notice_app_closed
                else -> null
            }

            val showTopNotice = uiState.endReason == "app_closed" ||
                uiState.endReason == "abandoned_disconnect" ||
                uiState.wasInterrupted ||
                uiState.loadError

            // --- Header ---
            Text(
                text = stringResource(headerRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(subheaderRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            if (noticeRes != null && showTopNotice) {
                Spacer(modifier = Modifier.height(20.dp))
                SummaryNoticeBanner(noticeRes = noticeRes)
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }

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
                        text = stringResource(R.string.summary_leaves_earned),
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
                        text = stringResource(R.string.summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Time Row
                    SummaryRow(
                        label = stringResource(R.string.summary_time_focused),
                        value = timeString,
                        icon = Icons.Default.AccessTime
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Tasks Row
                    SummaryRow(
                        label = stringResource(R.string.summary_tasks_finished),
                        value = stringResource(R.string.summary_tasks_count, uiState.tasksFinished),
                        icon = Icons.Default.CheckCircleOutline
                    )
                }
            }

            if (!showTopNotice && noticeRes != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SummaryNoticeBanner(noticeRes = noticeRes)
            }
        }

        // --- Continue Button ---
        Box(modifier = Modifier.padding(top = 0.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Button(
                onClick = onContinueClick,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.action_continue), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SummaryNoticeBanner(noticeRes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(noticeRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
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