package com.grepho.cozydoubling.ui.pages


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

// Represents the user's current subscription/supporter status
data class UserMonetizationState(
    val isSupporter: Boolean,
    val hasCozyPass: Boolean
)

data class ThemeItemUiState(
    val id: String,
    val name: String,
    val primaryColor: Color, // Shows a preview of the theme
    val leafPrice: Int,
    val iapPrice: String,    // e.g., "$1.99"
    val isPremium: Boolean,
    val isOwned: Boolean,
    val isEquipped: Boolean = false // Used for Inventory
)

@Composable
fun ShopPage(
    themes: List<ThemeItemUiState>,
    userState: UserMonetizationState
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(themes) { theme ->
            ThemeShopCard(theme = theme, userState = userState)
        }
    }
}

@Composable
fun ThemeShopCard(theme: ThemeItemUiState, userState: UserMonetizationState) {
    val isEffectivelyOwned = theme.isOwned || userState.hasCozyPass

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Theme Color Preview
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

            Text(
                text = if (theme.isPremium) "Premium Theme" else "Basic Theme",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Purchase Logic
            if (isEffectivelyOwned) {
                // OWNED STATE
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Owned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Owned",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else if (!theme.isPremium) {
                // BASIC THEME - Buy with leaves directly
                Button(
                    onClick = { /* Buy with leaves */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("${theme.leafPrice} Leaves")
                }
            } else {
                // PREMIUM THEME - Show IAP and conditional Leaf purchase
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Cash Purchase
                    Button(
                        onClick = { /* Trigger IAP */ },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(theme.iapPrice)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. Leaf Purchase (Locked if not Supporter)
                    if (userState.isSupporter) {
                        OutlinedButton(
                            onClick = { /* Buy with leaves */ },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("${theme.leafPrice} Leaves")
                        }
                    } else {
                        // Locked Supporter Perk
                        Surface(
                            onClick = { /* Open Supporter Upsell Dialog */ },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${theme.leafPrice} Leaves",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}