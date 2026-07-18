package com.grepho.cozydoubling.features.shop

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

// Represents the user's current subscription/supporter status
data class UserMonetizationState(
    val isSupporter: Boolean,
    val hasCozyPass: Boolean
)

/**
 * A full color role palette for a theme.
 * This is intentionally the SAME shape used both to (a) apply the theme
 * in the real app and (b) render the shop preview mockup. There is no
 * separate "preview color" concept — a theme cannot look different in
 * the shop than it does once equipped, by construction.
 *
 * These fields are exactly the MaterialTheme.colorScheme roles this
 * codebase reads, audited via `grep -r "MaterialTheme.colorScheme\."`
 * across the whole app (Shop, Room, Friends, Journey, Inventory,
 * Summary, Home, Settings, CozyTopBar):
 *   - primary / onPrimary            -> HomePage CTA button (solid fill)
 *   - primaryContainer/onPrimaryContainer -> avatars (Room, Friends, Journey, Summary)
 *   - secondaryContainer/onSecondaryContainer -> badges/chips (Shop, Journey)
 *   - surfaceVariant / onSurfaceVariant -> cards, sheets, subtitles (everywhere)
 *   - onSurface -> body text (Room task list)
 *   - background -> screen background (Scaffold default, all screens)
 *
 * NOT included on purpose: `error`. Delete/destructive actions
 * (SettingsPage) intentionally keep a fixed, non-themed error color —
 * a "Delete Account" button shouldn't get softer just because someone
 * bought a pastel theme. Define error once in the app's base theme,
 * not per-theme in Supabase.
 *
 * If a new screen reads a role not listed here, add it before shipping
 * any theme that would leave that screen with an undefined color.
 */
data class ThemePalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val onSurface: Color,
    val background: Color
)

data class ThemeItemUiState(
    val id: String,
    val name: String,
    val palette: ThemePalette,
    val leafPrice: Int,
    val iapPrice: String,        // formatted, localized price string from Billing (e.g. "$1.99")
    val isPremium: Boolean,
    val isOwned: Boolean,
    val isEquipped: Boolean = false
)

// =========================================================
// Supabase DTOs — raw rows come back as hex strings, not Color.
// Keep parsing at the network boundary; the rest of the app
// only ever touches ThemePalette / androidx.compose.ui.graphics.Color.
// =========================================================

@Serializable
data class ThemeColorsDto(
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val secondaryContainer: String,
    val onSecondaryContainer: String,
    val surfaceVariant: String,
    val onSurfaceVariant: String,
    val onSurface: String,
    val background: String
)

@Serializable
data class ThemeDto(
    val id: String,
    val name: String,
    val is_premium: Boolean,
    val leaf_price: Int,
    val iap_product_id: String? = null,
    val sort_order: Int = 0,
    val colors: ThemeColorsDto
)

fun ThemeColorsDto.toPalette(): ThemePalette = ThemePalette(
    primary = Color(android.graphics.Color.parseColor(primary)),
    onPrimary = Color(android.graphics.Color.parseColor(onPrimary)),
    primaryContainer = Color(android.graphics.Color.parseColor(primaryContainer)),
    onPrimaryContainer = Color(android.graphics.Color.parseColor(onPrimaryContainer)),
    secondaryContainer = Color(android.graphics.Color.parseColor(secondaryContainer)),
    onSecondaryContainer = Color(android.graphics.Color.parseColor(onSecondaryContainer)),
    surfaceVariant = Color(android.graphics.Color.parseColor(surfaceVariant)),
    onSurfaceVariant = Color(android.graphics.Color.parseColor(onSurfaceVariant)),
    onSurface = Color(android.graphics.Color.parseColor(onSurface)),
    background = Color(android.graphics.Color.parseColor(background))
)