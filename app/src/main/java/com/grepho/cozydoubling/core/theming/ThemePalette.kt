package com.grepho.cozydoubling.core.theming

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

data class ThemePalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,          // Added
    val onSecondary: Color,        // Added
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,           // Added
    val onTertiary: Color,         // Added
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,       // Added
    val surface: Color,            // Added
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
)
fun ThemePalette.toColorScheme() = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant
)