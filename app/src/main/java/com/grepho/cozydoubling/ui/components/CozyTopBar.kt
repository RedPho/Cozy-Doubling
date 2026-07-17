package com.grepho.cozydoubling.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CozyTopBar(
    appName: String,
    currencyCount: Int,
    onShopClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFriendsClick: () -> Unit
) {
    // State to track if the avatar dropdown is open
    var isMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(text = appName)
        },
        navigationIcon = {
            Box(modifier = Modifier.padding(start = 8.dp)) {
                // 1. Circle Avatar
                // Replace Modifier.background with your actual image:
                // Image(painter = painterResource(id = R.drawable.your_avatar), ...)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                        .clickable { isMenuExpanded = true }
                )

                // 2. Dropdown Menu attached to the avatar
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Account") },
                        onClick = {
                            isMenuExpanded = false
                            onProfileClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            isMenuExpanded = false
                            onSettingsClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Friends") },
                        onClick = {
                            isMenuExpanded = false
                            onFriendsClick()
                        }
                    )
                }
            }
        },
        actions = {
            // 3. Currency Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onShopClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart, // Replace with your coin/currency icon
                    contentDescription = "Shop"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currencyCount.toString(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    )
}