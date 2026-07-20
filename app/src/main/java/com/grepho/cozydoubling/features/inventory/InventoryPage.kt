package com.grepho.cozydoubling.features.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.features.shop.ThemeMockupPreview
import androidx.compose.foundation.lazy.items
import com.grepho.cozydoubling.features.shop.ShopItemUiState


// --- THE SCREEN ENTRY POINT ---
@Composable
fun InventoryScreen(viewModel: InventoryViewModel = viewModel()) {
    val ownedThemes by viewModel.ownedThemes.collectAsState()
    InventoryPage(
        ownedThemes = ownedThemes,
        onEquipClick = { themeId -> viewModel.onEquipClicked(themeId) }
    )
}

// --- THE UI COMPONENT ---
@Composable
fun InventoryPage(
    ownedThemes: List<ShopItemUiState.Theme>,
    onEquipClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        item {
            // Header: Owned Themes + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Owned Themes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                    Text(
                        text = "${ownedThemes.size} Themes",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        items(ownedThemes) { theme ->
            ThemeInventoryCard(theme, onEquipClick = { onEquipClick(theme.id) })
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            // "Get More Themes" Bottom Button
            Button(
                onClick = { /* Navigate to Shop Tab */ },
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(vertical = 8.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get More Themes")
            }
        }
    }
}

@Composable
fun ThemeInventoryCard(
    theme: ShopItemUiState.Theme,
    onEquipClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = if (theme.isEquipped) BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- 1. The Large Mockup Preview ---
            ThemeMockupPreview(
                palette = theme.palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp) // Large preview as per design
            )

            Spacer(modifier = Modifier.height(20.dp))

            // --- 2. Header Info Row ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = theme.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Action Button / Badge on the right
                if (theme.isEquipped) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Equipped", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                } else {
                    IconButton(
                        onClick = onEquipClick,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checkroom, // The "Hanger" icon from design
                            contentDescription = "Apply",
                            tint = Color.Black
                        )
                    }
                }
            }
        }
    }
}