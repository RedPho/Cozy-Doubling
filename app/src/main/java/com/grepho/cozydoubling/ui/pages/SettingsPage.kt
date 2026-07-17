package com.grepho.cozydoubling.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Account", style = MaterialTheme.typography.titleMedium)

        // Edit Username Button
        ListItem(
            headlineContent = { Text("Edit Username") },
            supportingContent = { Text("Current: CozyPanda") },
            modifier = Modifier.clickable { /* Open edit dialog */ }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Danger Zone
        Text(
            text = "Danger Zone",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        ListItem(
            headlineContent = { Text("Delete Account", color = MaterialTheme.colorScheme.error) },
            supportingContent = { Text("This cannot be undone.") },
            modifier = Modifier.clickable { /* Open confirm dialog */ }
        )
    }
}