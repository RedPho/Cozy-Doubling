package com.grepho.cozydoubling.features.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository

// --- THE SCREEN ENTRY POINT ---
@Composable
fun FriendsScreen(viewModel: FriendsViewModel = viewModel()) {
    // 1. Collect both flows from the ViewModel
    val friendsList by viewModel.friends.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()

    val profile by ProfileRepository.profile.collectAsState()


    FriendsPage(
        myTag = profile?.playerTag ?: "", // Pass your tag
        friendsList = friendsList,
        pendingRequests = pendingRequests,
        onAddFriend = { tag -> viewModel.onSendRequest(tag) },
        onAcceptRequest = { id -> viewModel.onAcceptRequest(id) }
    )
}

// --- THE UI COMPONENT ---
@Composable
fun FriendsPage(
    myTag: String,
    friendsList: List<FriendUiState>,
    pendingRequests: List<Profile>,
    onAddFriend: (String) -> Unit,
    onAcceptRequest: (String) -> Unit
) {
    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var newFriendTag by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (myTag.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Your Friend Code:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "#$myTag",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }


        // --- Add Friend Button ---
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Friend")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Friend")
        }

        // --- Friends List ---
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. SHOW PENDING REQUESTS
            if (pendingRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending Requests",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(pendingRequests) { request ->
                    PendingRequestCard(
                        name = request.displayName,
                        tag = request.playerTag,
                        onAccept = { onAcceptRequest(request.id) }
                    )
                }
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            // 2. SHOW REAL FRIENDS
            item {
                Text(
                    text = "Your Friends",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(friendsList) { friend ->
                FriendCard(friend = friend)
            }
        }
    }

    // --- Add Friend Dialog ---
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add a Friend") },
            text = {
                OutlinedTextField(
                    value = newFriendTag,
                    onValueChange = { newFriendTag = it },
                    placeholder = { Text("Enter Friend Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddFriend(newFriendTag)
                        newFriendTag = ""
                        showAddDialog = false
                    }
                ) {
                    Text("Send Request")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newFriendTag = ""
                        showAddDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
fun PendingRequestCard(
    name: String,
    tag: String,
    onAccept: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleSmall)
                Text(text = "#$tag", style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onAccept) {
                Text("Accept")
            }
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
            // --- 1. Simple Avatar (Just the initial) ---
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

            Spacer(modifier = Modifier.width(16.dp))

            // --- 2. Identity & The Story ---
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${friend.name} #${friend.playerTag}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // The "Story" logic
                val storyText = if (friend.lastTaskText != null) {
                    // TODO: Format 'lastSessionDate' to be relative like "Yesterday" later
                    "${friend.lastSessionDate?.take(10)} focused ${friend.lastSessionDuration}m on ${friend.lastTaskText}"
                } else {
                    "Just joined! No sessions yet."
                }

                Text(
                    text = storyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}