package com.grepho.cozydoubling.features.shop

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.theming.ThemePalette

// Represents the user's current subscription/supporter status

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



sealed class ShopItemUiState {
    abstract val id: String
    abstract val name: String
    abstract val isPremium: Boolean
    abstract val isOwned: Boolean

    data class Theme(
        override val id: String,
        override val name: String,
        override val isPremium: Boolean,
        override val isOwned: Boolean,
        val palette: ThemePalette,
        val leafPrice: Int,
        val isEquipped: Boolean = false
    ) : ShopItemUiState()

    data class Pass(
        override val id: String,
        override val name: String,
        override val isPremium: Boolean,
        override val isOwned: Boolean,
        val iapId: String,
        val priceString: String // e.g. "$4.99"
    ) : ShopItemUiState()
}