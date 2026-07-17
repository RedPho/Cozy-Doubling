package com.grepho.cozydoubling.features.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

// --- THE SCREEN ENTRY POINT ---
@Composable
fun FriendsScreen(viewModel: FriendsViewModel = viewModel()) {
    val friendsList by viewModel.friends.collectAsState()

    FriendsPage(friendsList = friendsList)
}

// --- THE UI COMPONENT ---
@Composable
fun FriendsPage(friendsList: List<FriendUiState>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(friendsList) { friend ->
            FriendCard(friend = friend)
        }
    }
}

@Composable
fun FriendCard(friend: FriendUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- 1. Avatar & Status Dot ---
            Box(contentAlignment = Alignment.BottomEnd) {
                // Avatar Box
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friend.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Online Indicator
                if (friend.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)) // Cozy Green
                            .padding(2.dp) // Creates a tiny border effect if wrapped, or just leave solid
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // --- 2. Name & Last Task ---
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = friend.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${friend.lastActiveText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Task: ${friend.lastTask}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // --- 3. Leaves Stat ---
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${friend.totalLeaves}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Leaves",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Preview with Mock Data ---
@Preview(showBackground = true)
@Composable
fun FriendsPagePreview() {
    MaterialTheme {
        val mockFriends = listOf(
            FriendUiState(
                name = "Alex",
                isOnline = true,
                lastActiveText = "Focusing",
                lastTask = "Writing email drafts",
                totalLeaves = 450
            ),
            FriendUiState(
                name = "Sam",
                isOnline = false,
                lastActiveText = "Resting",
                lastTask = "Organized the desk",
                totalLeaves = 1200
            )
        )
        // Wrapped in a box with a background to simulate the Oasis lower half
        Box(modifier = Modifier.height(400.dp).background(MaterialTheme.colorScheme.background)) {
            FriendsPage(friendsList = mockFriends)
        }
    }
}