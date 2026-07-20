package com.grepho.cozydoubling.ui.theme

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.theming.ThemePalette

object CozyPalettes {
    val SystemDefault = ThemePalette(
        primary = PrimarySage,
        onPrimary = OnPrimaryWhite,
        primaryContainer = PrimaryContainerSage,
        onPrimaryContainer = OnPrimaryContainerDark,
        secondary = SecondaryBrown,
        onSecondary = OnSecondaryWhite,
        secondaryContainer = SecondaryContainerPeach,
        onSecondaryContainer = OnSecondaryContainerBrown,
        tertiary = TertiaryGold,
        onTertiary = Color.White,
        tertiaryContainer = TertiaryContainerGold,
        background = BackgroundCream,
        onBackground = OnBackgroundCharcoal,
        surface = SurfaceWhite,
        onSurface = OnSurfaceCharcoal,
        surfaceVariant = SurfaceVariantGrey,
        onSurfaceVariant = OnSurfaceVariantSage
    )
}