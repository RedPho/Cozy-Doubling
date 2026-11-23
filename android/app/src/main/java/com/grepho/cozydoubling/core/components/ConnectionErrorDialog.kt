package com.grepho.cozydoubling.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.grepho.cozydoubling.R
import com.grepho.cozydoubling.core.network.ConnectionStateManager

@Composable
fun ConnectionErrorDialog(
    state: ConnectionStateManager.ConnectionState,
    onRetry: () -> Unit
) {
    if (state == ConnectionStateManager.ConnectionState.Available) return

    val isRefreshing = state is ConnectionStateManager.ConnectionState.Refreshing

    val title = when (state) {
        ConnectionStateManager.ConnectionState.Offline -> stringResource(R.string.network_no_internet)
        ConnectionStateManager.ConnectionState.ServerError -> stringResource(R.string.network_error_title)
        ConnectionStateManager.ConnectionState.Refreshing -> stringResource(R.string.network_retrying)
        else -> return
    }

    val message = when (state) {
        ConnectionStateManager.ConnectionState.Offline -> stringResource(R.string.network_error_message)
        ConnectionStateManager.ConnectionState.ServerError -> stringResource(R.string.network_server_error)
        ConnectionStateManager.ConnectionState.Refreshing -> stringResource(R.string.network_loading_message)
        else -> return
    }

    val icon = when (state) {
        ConnectionStateManager.ConnectionState.Offline -> Icons.Default.SignalWifiOff
        ConnectionStateManager.ConnectionState.ServerError -> Icons.Default.CloudOff
        ConnectionStateManager.ConnectionState.Refreshing -> Icons.Default.CloudOff
        else -> return
    }

    Dialog(
        onDismissRequest = { /* Blocking */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onRetry,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRefreshing) {
                        Text(stringResource(R.string.network_wait))
                    } else {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}
