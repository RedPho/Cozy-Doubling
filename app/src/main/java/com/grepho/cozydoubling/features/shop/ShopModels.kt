package com.grepho.cozydoubling.features.shop

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.theming.ThemePalette

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
