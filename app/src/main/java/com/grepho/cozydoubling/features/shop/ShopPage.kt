package com.grepho.cozydoubling.features.shop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import com.grepho.cozydoubling.core.theming.ThemePalette
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Stars

// --- THE SCREEN ENTRY POINT ---
@Composable
fun ShopScreen(viewModel: ShopViewModel = viewModel()) {
    // 1. Collect the correct flow (items, not themes)
    val items by viewModel.items.collectAsState()
    val isSupporter by viewModel.isSupporter.collectAsState()

    ShopPage(
        items = items,
        isSupporter = isSupporter,
        onBuyWithLeaves = { id -> viewModel.onBuyWithLeavesClicked(id) },
        onBuyWithCash = { id -> viewModel.onBuyWithCashClicked(id) }
    )
}

// --- THE UI COMPONENT ---
@Composable
fun ShopPage(
    items: List<ShopItemUiState>, // Changed from themes
    isSupporter: Boolean,
    onBuyWithLeaves: (String) -> Unit,
    onBuyWithCash: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items) { item ->
            when (item) {
                is ShopItemUiState.Pass -> {
                    SupporterPassCard(item, onBuy = { onBuyWithCash(item.id) })
                }
                is ShopItemUiState.Theme -> {
                    ThemeShopCard(item, isSupporter, onBuyWithLeaves, onBuyWithCash)
                }
            }
        }
    }
}

@Composable
fun SupporterPassCard(pass: ShopItemUiState.Pass, onBuy: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Stars, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(text = pass.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "Unlocks all Premium Themes", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onBuy, shape = CircleShape) {
                Text(pass.priceString)
            }
        }
    }
}

@Composable
fun ThemeShopCard(
    theme: ShopItemUiState.Theme, // Changed to use the new sealed class variant
    isSupporter: Boolean,
    onBuyWithLeaves: (String) -> Unit,
    onBuyWithCash: (String) -> Unit
) {
    // NEW LOGIC: Available if owned (basic) OR user has a pass (premium)
    val isAvailable = if (theme.isPremium) isSupporter else theme.isOwned

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ThemeMockupPreview(palette = theme.palette, modifier = Modifier.fillMaxWidth().height(200.dp))
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = theme.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = if (theme.isPremium) "Premium Theme" else "Basic Theme", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                if (isAvailable) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                } else {
                    Button(
                        onClick = { if (theme.isPremium) onBuyWithCash(theme.id) else onBuyWithLeaves(theme.id) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (theme.isPremium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (theme.isPremium) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text(if (theme.isPremium) "Get Pass" else "${theme.leafPrice} Leaves")
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeMockupPreview(
    palette: ThemePalette,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(palette.background) // This will now show the greenish-mint correctly
            .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Swatches (Shows the Browns clearly)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(palette.primary, palette.secondary, palette.tertiary).forEach { color ->
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(color))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 2. Realistic Mini Components
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                // Mini Focus Ring (Dominant Primary)
                Box(
                    modifier = Modifier.size(70.dp).border(4.dp, palette.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(50.dp).background(palette.primaryContainer.copy(alpha = 0.4f), CircleShape))
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Mini Card (Surface - now smaller to not hide the background)
                Surface(
                    modifier = Modifier.size(70.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = palette.surface, // This is the White part
                    shadowElevation = 2.dp
                ) {
                    // Small Reward Badge using Tertiary (Brown)
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(24.dp).background(palette.tertiary, CircleShape))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 3. Mini App Bar / Selection Pill using Secondary (Brown)
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(20.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(palette.secondary.copy(alpha = 0.2f))
            )
        }
    }
}