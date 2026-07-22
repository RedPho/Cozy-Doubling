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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.grepho.cozydoubling.core.network.ConnectionStateManager

@Composable
fun ConnectionErrorDialog(
    state: ConnectionStateManager.ConnectionState,
    onRetry: () -> Unit
) {
    if (state == ConnectionStateManager.ConnectionState.Available) return

    val isRefreshing = state is ConnectionStateManager.ConnectionState.Refreshing

    val (title, message, icon) = when (state) {
        ConnectionStateManager.ConnectionState.Offline -> Triple(
            "No Internet",
            "It looks like you're offline. Please check your connection and try again.",
            Icons.Default.SignalWifiOff
        )
        ConnectionStateManager.ConnectionState.ServerError -> Triple(
            "Connection Problem",
            "We're having trouble reaching our servers. Please try again in a moment.",
            Icons.Default.CloudOff
        )
        ConnectionStateManager.ConnectionState.Refreshing -> Triple(
            "Retrying...",
            "Checking connection and refreshing your data.",
            Icons.Default.CloudOff // Or a different icon
        )
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
                        Text("Please wait...")
                    } else {
                        Text("Retry Connection")
                    }
                }
            }
        }
    }
}
