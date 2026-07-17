package com.grepho.cozydoubling.features.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.features.shop.ThemeItemUiState

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
    ownedThemes: List<ThemeItemUiState>,
    onEquipClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(ownedThemes) { theme ->
            ThemeInventoryCard(
                theme = theme,
                onEquipClick = { onEquipClick(theme.id) }
            )
        }
    }
}

@Composable
fun ThemeInventoryCard(
    theme: ThemeItemUiState,
    onEquipClick: () -> Unit
) {
    val cardModifier = if (theme.isEquipped) {
        Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
    } else {
        Modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(theme.primaryColor)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = theme.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (theme.isEquipped) {
                Button(
                    onClick = { /* Do nothing, already equipped */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Equipped")
                }
            } else {
                OutlinedButton(
                    onClick = onEquipClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Equip")
                }
            }
        }
    }
}