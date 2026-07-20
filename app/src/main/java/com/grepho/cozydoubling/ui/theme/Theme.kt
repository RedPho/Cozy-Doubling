package com.grepho.cozydoubling.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.core.theming.toColorScheme


private val DarkColorScheme = darkColorScheme(
    primary = PrimarySage,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerSage,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryBrown,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerPeach,
    onSecondaryContainer = OnSecondaryContainerBrown,
    tertiary = TertiaryGold,
    tertiaryContainer = TertiaryContainerGold,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    error = ErrorRed,
    outline = OutlineGrey
)

private val LightColorScheme = lightColorScheme(
    primary = PrimarySage,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerSage,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryBrown,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerPeach,
    onSecondaryContainer = OnSecondaryContainerBrown,
    tertiary = TertiaryGold,
    tertiaryContainer = TertiaryContainerGold,
    background = BackgroundCream,
    surface = SurfaceWhite,
    onBackground = OnBackgroundCharcoal,
    onSurface = OnSurfaceCharcoal,
    error = ErrorRed,
    outline = OutlineGrey
)


@Composable
fun CozyDoublingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    // Add this parameter!
    customPalette: ThemePalette? = null,
    content: @Composable () -> Unit
) {
    // 1. Logic to pick the color scheme
    val colorScheme = when {
        customPalette != null -> customPalette.toColorScheme()

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}