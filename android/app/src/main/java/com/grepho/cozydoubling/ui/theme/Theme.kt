package com.grepho.cozydoubling.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.core.theming.toColorScheme
import com.grepho.cozydoubling.core.validation.TextLimits


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
    dynamicColor: Boolean = false,
    // Add this parameter!
    customPalette: ThemePalette? = null,
    content: @Composable () -> Unit
) {
    // 1. Logic to pick the color scheme
    val colorScheme = when {
        customPalette != null -> customPalette.toColorScheme()
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = 
                colorScheme.background.luminance() > 0.5f
        }
    }

    val currentDensity = LocalDensity.current
    val cappedDensity = Density(
        density = currentDensity.density,
        fontScale = TextLimits.clampFontScale(currentDensity.fontScale)
    )

    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}