package com.grepho.cozydoubling.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

// --- THE SCREEN ENTRY POINT ---
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsPage(
        uiState = uiState,
        onSaveUsername = { viewModel.onUpdateUsername(it) },
        onSignOut = { viewModel.onSignOut() },
        onDeleteAccount = { viewModel.onDeleteAccount() }
    )
}

// --- THE UI COMPONENT ---
@Composable
fun SettingsPage(
    uiState: SettingsUiState,
    onSaveUsername: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    // Local UI states for showing dialogs
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var tempUsername by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Account", style = MaterialTheme.typography.titleMedium)

        ListItem(
            headlineContent = { Text("Edit Username") },
            supportingContent = { Text("Current: ${uiState.username}") },
            modifier = Modifier.clickable {
                tempUsername = uiState.username
                showUsernameDialog = true
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ListItem(
            headlineContent = { Text("Sign Out") },
            modifier = Modifier.clickable { onSignOut() }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Danger Zone",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        ListItem(
            headlineContent = { Text("Delete Account", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("This cannot be undone.") },
            modifier = Modifier.clickable { showDeleteDialog = true }
        )
    }

    // --- DIALOGS ---

    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = { Text("Edit Username") },
            text = {
                OutlinedTextField(
                    value = tempUsername,
                    onValueChange = { tempUsername = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveUsername(tempUsername)
                        showUsernameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure? This will permanently delete your stats, leaves, and friends list. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}