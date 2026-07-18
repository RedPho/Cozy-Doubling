package com.grepho.cozydoubling.features.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.core.theming.ThemePalette

// --- THE SCREEN ENTRY POINT ---
@Composable
fun ShopScreen(viewModel: ShopViewModel = viewModel()) {
    val themes by viewModel.themes.collectAsState()
    val userState by viewModel.userState.collectAsState()

    ShopPage(
        themes = themes,
        userState = userState,
        onBuyWithLeaves = { themeId -> viewModel.onBuyWithLeavesClicked(themeId) },
        onBuyWithCash = { themeId -> viewModel.onBuyWithCashClicked(themeId) }
    )
}

// --- THE UI COMPONENT ---
@Composable
fun ShopPage(
    themes: List<ThemeItemUiState>,
    userState: UserMonetizationState,
    onBuyWithLeaves: (String) -> Unit,
    onBuyWithCash: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(themes) { theme ->
            ThemeShopCard(
                theme = theme,
                userState = userState,
                onBuyWithLeaves = { onBuyWithLeaves(theme.id) },
                onBuyWithCash = { onBuyWithCash(theme.id) }
            )
        }
    }
}

@Composable
fun ThemeShopCard(
    theme: ThemeItemUiState,
    userState: UserMonetizationState,
    onBuyWithLeaves: () -> Unit,
    onBuyWithCash: () -> Unit
) {
    val isEffectivelyOwned = theme.isOwned || userState.hasCozyPass

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Replaces the single-hue circle: a truthful mini mockup
            // rendered straight from the theme's full palette.
            ThemeMiniPreview(
                palette = theme.palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isEffectivelyOwned) {
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
                    Button(
                        onClick = onBuyWithLeaves,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("${theme.leafPrice} Leaves")
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onBuyWithCash,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(theme.iapPrice)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (userState.isSupporter) {
                            OutlinedButton(
                                onClick = onBuyWithLeaves,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("${theme.leafPrice} Leaves")
                            }
                        } else {
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
}

/**
 * A tiny, truthful preview built out of MINIATURES OF THE REAL COMPONENTS,
 * not an invented mockup. Every shape here corresponds to something that
 * actually renders somewhere in the app with these exact roles:
 *
 *   - the circle  -> ParticipantAvatar / Friends / Journey (primaryContainer / onPrimaryContainer)
 *   - the pill     -> the "Owned" badge (secondaryContainer / onSecondaryContainer)
 *   - the small solid button -> HomePage CTA (primary / onPrimary) — the
 *     riskiest pairing to preview, since it's a solid fill rather than a
 *     soft container: bad contrast here is the most likely way a theme
 *     "doesn't preview well"
 *   - the bottom bar -> TaskBottomSheet surface (surfaceVariant / onSurfaceVariant)
 *   - the two text bars -> task list text (onSurface) on `background`
 *   - the ring stroke -> progress ring (primary)
 *
 * If this composable ever uses a color that isn't ThemePalette, that's a bug —
 * it means the preview is showing something the app can't actually produce.
 */
@Composable
fun ThemeMiniPreview(
    palette: ThemePalette,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.background)
            .border(1.dp, palette.primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mini avatar — same visual as ParticipantAvatar
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(palette.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Y",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // progress ring hint, same role as the real ring (`primary`)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(2.dp, palette.primary, CircleShape)
                    )
                }

                // Mini "Owned" badge — same roles as the real badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Owned",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onSecondaryContainer
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Mini solid CTA — same roles as the HomePage button.
                // Solid fills are where a bad on/container pairing is most
                // visible, so this is the single most important swatch here.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.primary)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Go",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Mini task sheet strip — same roles as TaskBottomSheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(palette.surfaceVariant)
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.onSurfaceVariant.copy(alpha = 0.9f))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.onSurface.copy(alpha = 0.6f))
                )
            }
        }
    }
}