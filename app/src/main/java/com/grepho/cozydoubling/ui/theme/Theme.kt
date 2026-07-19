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

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun CozyDoublingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    // Add this parameter!
    customPalette: ThemePalette? = null,
    content: @Composable () -> Unit
) {
    // 1. Logic to pick the color scheme
    val colorScheme = when {
        // A. Use the custom palette if available (This is the Magic!)
        customPalette != null -> {
            lightColorScheme(
                primary = customPalette.primary,
                onPrimary = customPalette.onPrimary,
                primaryContainer = customPalette.primaryContainer,
                onPrimaryContainer = customPalette.onPrimaryContainer,
                secondaryContainer = customPalette.secondaryContainer,
                onSecondaryContainer = customPalette.onSecondaryContainer,
                surfaceVariant = customPalette.surfaceVariant,
                onSurfaceVariant = customPalette.onSurfaceVariant,
                onSurface = customPalette.onSurface,
                background = customPalette.background,
                surface = customPalette.background // Map surface to background for a cozy look
            )
        }

        // B. Fallback to Dynamic/System colors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}