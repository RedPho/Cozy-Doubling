package com.grepho.cozydoubling.features.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.BuildConfig
import com.grepho.cozydoubling.R
import com.grepho.cozydoubling.core.safety.BlockedUser
import com.grepho.cozydoubling.core.validation.TextLimits

// --- THE SCREEN ENTRY POINT ---
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit, // Add this parameter
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    SettingsPage(
        uiState = uiState,
        onBackClick = onBackClick,
        onSaveUsername = { viewModel.onUpdateUsername(it) },
        onOpenBlockedUsers = { viewModel.onOpenBlockedUsers() },
        onUnblockUser = { viewModel.onUnblockUser(it) },
        onTogglePip = { viewModel.onTogglePip(it) },
        onSignOut = { viewModel.onSignOut() },
        onDeleteAccount = { viewModel.onDeleteAccount() },
        onManageSubscription = { viewModel.onManageSubscription(context) },
        onRestorePurchases = {
            viewModel.onRestorePurchases { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        },
        onOpenUrl = { url -> viewModel.onOpenUrl(context, url) }
    )
}


// --- THE UI COMPONENT ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    uiState: SettingsUiState,
    onBackClick: () -> Unit,
    onSaveUsername: (String) -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onUnblockUser: (String) -> Unit,
    onTogglePip: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestorePurchases: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBlockedUsersSheet by remember { mutableStateOf(false) }
    var pendingUnblockUser by remember { mutableStateOf<BlockedUser?>(null) }
    var tempUsername by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_ok))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(32.dp))

            // --- ACCOUNT DETAILS ---
            SettingsGroup(title = stringResource(R.string.settings_group_account)) {
                SettingsItem(
                    label = stringResource(R.string.settings_label_username),
                    value = uiState.username,
                    icon = Icons.Default.Edit,
                    onClick = {
                        tempUsername = uiState.username
                        showUsernameDialog = true
                    }
                )
                
                if (uiState.isSupporter) {
                    SettingsItem(
                        label = stringResource(R.string.settings_label_subscription),
                        value = stringResource(R.string.settings_subscription_active),
                        icon = Icons.Default.CardMembership,
                        onClick = onManageSubscription
                    )
                }

                SettingsItem(
                    label = stringResource(R.string.settings_label_restore),
                    value = stringResource(R.string.settings_restore_value),
                    icon = Icons.Default.Refresh,
                    onClick = onRestorePurchases
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- FOCUS & SAFETY ---
            SettingsGroup(title = stringResource(R.string.settings_group_focus_safety)) {
                SettingsToggleItem(
                    label = stringResource(R.string.settings_label_pip_mode),
                    subtitle = stringResource(R.string.settings_pip_subtitle),
                    icon = Icons.Default.PictureInPictureAlt,
                    checked = uiState.isPipEnabled,
                    onCheckedChange = onTogglePip
                )

                val blockedSummary = if (uiState.blockedUsers.isEmpty()) {
                    stringResource(R.string.settings_blocked_none)
                } else {
                    stringResource(R.string.settings_blocked_count, uiState.blockedUsers.size)
                }
                SettingsItem(
                    label = stringResource(R.string.settings_label_blocked_users),
                    value = blockedSummary,
                    icon = Icons.Default.Block,
                    onClick = {
                        onOpenBlockedUsers()
                        showBlockedUsersSheet = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- SESSION ACTIONS ---
            SettingsGroup(title = stringResource(R.string.settings_group_session)) {
                SettingsItem(
                    label = stringResource(R.string.settings_label_signout),
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    onClick = onSignOut
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- LEGAL & ABOUT ---
            SettingsGroup(title = stringResource(R.string.settings_group_legal)) {
                SettingsItem(
                    label = stringResource(R.string.settings_label_privacy),
                    icon = Icons.Default.Policy,
                    onClick = { onOpenUrl(BuildConfig.PRIVACY_POLICY_URL) }
                )
                SettingsItem(
                    label = stringResource(R.string.settings_label_terms),
                    icon = Icons.Default.Description,
                    onClick = { onOpenUrl(BuildConfig.TERMS_OF_SERVICE_URL) }
                )
                
                val context = LocalContext.current
                val packageInfo = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0)
                    } catch (e: Exception) { null }
                }
                val version = packageInfo?.versionName ?: "1.0"
                val code = packageInfo?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        it.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        it.versionCode.toLong()
                    }
                } ?: 1

                SettingsItem(
                    label = stringResource(R.string.settings_label_version),
                    value = "$version ($code)",
                    icon = Icons.Default.Info,
                    onClick = { /* No-op */ }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- DANGER ZONE ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.settings_group_danger),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.settings_danger_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.settings_delete_button), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // --- DIALOGS ---

    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = { Text(stringResource(R.string.settings_edit_username)) },
            text = {
                OutlinedTextField(
                    value = tempUsername,
                    onValueChange = { tempUsername = TextLimits.limitInput(it, TextLimits.MAX_DISPLAY_NAME_LENGTH) },
                    singleLine = true,
                    supportingText = {
                        Text("${tempUsername.length}/${TextLimits.MAX_DISPLAY_NAME_LENGTH}")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_username_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveUsername(tempUsername)
                        showUsernameDialog = false
                    },
                    enabled = tempUsername.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_delete_account)) },
            text = { Text(stringResource(R.string.settings_delete_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showBlockedUsersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBlockedUsersSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_label_blocked_users),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.blockedUsers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_blocked_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    uiState.blockedUsers.forEach { blocked ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = blocked.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "#${blocked.playerTag}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { pendingUnblockUser = blocked }) {
                                    Text(stringResource(R.string.action_unblock))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingUnblockUser?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingUnblockUser = null },
            title = { Text(stringResource(R.string.settings_unblock_confirm_title, target.displayName)) },
            text = { Text(stringResource(R.string.settings_unblock_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onUnblockUser(target.id)
                        pendingUnblockUser = null
                    }
                ) {
                    Text(stringResource(R.string.action_unblock))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnblockUser = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsToggleItem(
    label: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Color.Gray,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
fun SettingsItem(
    label: String,
    value: String? = null,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(text = value, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}