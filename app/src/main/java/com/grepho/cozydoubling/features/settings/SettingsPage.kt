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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.BuildConfig
import com.grepho.cozydoubling.R

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
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestorePurchases: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
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