package com.grepho.cozydoubling.features.friends

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.profile.ProfileRepository
import kotlinx.coroutines.launch

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
    // 1. Add state for the Add Friend dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var newFriendTag by remember { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp) // Extra padding for the FAB
        ) {
            // --- 1. Connect Code Capsule ---
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "YOUR CONNECT CODE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val clipData = ClipData.newPlainText("Friend Code", myTag)
                                    clipboard.setClipEntry(ClipEntry(clipData))
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = myTag, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(12.dp))
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp), tint = Color.Gray)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // --- 2. Pending Requests ---
            if (pendingRequests.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Pending Requests (${pendingRequests.size})", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                items(pendingRequests) { request ->
                    PendingRequestCard(request.displayName, request.playerTag) { onAcceptRequest(request.id) }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            // --- 3. Your Garden (Friend List) ---
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Your Garden (${friendsList.size})", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(friendsList) { friend ->
                FriendCard(friend = friend)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 2. Add the Floating Action Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Friend")
        }
    }

    // 3. Add Friend Dialog
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddFriend(newFriendTag)
                        newFriendTag = ""
                        showAddDialog = false
                    },
                    shape = CircleShape
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
                    "Last focus time: ${friend.lastSessionDate?.take(10)} \nFocused: ${friend.lastSessionDuration}m \nLast Task: ${friend.lastTaskText}"
                } else {
                    "Just joined! No sessions yet."
                }

                Text(
                    text = storyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}