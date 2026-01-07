package com.zimbabeats.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.zimbabeats.data.AccentColor

/**
 * Creates a dark color scheme with the given accent color
 */
private fun createDarkColorScheme(accent: AccentColor): ColorScheme = darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primary.copy(alpha = 0.2f),
    onPrimaryContainer = accent.primary,

    secondary = accent.primaryVariant,
    onSecondary = accent.onPrimary,
    secondaryContainer = accent.primaryVariant.copy(alpha = 0.2f),
    onSecondaryContainer = accent.primaryVariant,

    tertiary = AccentPurple,
    onTertiary = Color.White,
    tertiaryContainer = AccentPurple.copy(alpha = 0.2f),
    onTertiaryContainer = AccentPurple,

    background = DarkBackground,
    onBackground = TextPrimaryDark,

    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,

    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceVariant,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = Color(0xFF404040),

    error = Color(0xFFCF6679),
    onError = Color.Black,

    outline = Color(0xFF3D3D3D),
    outlineVariant = Color(0xFF2A2A2A),

    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF121212),
    inversePrimary = accent.primaryVariant,

    scrim = Color.Black.copy(alpha = 0.6f)
)

/**
 * Creates a light color scheme with the given accent color
 */
private fun createLightColorScheme(accent: AccentColor): ColorScheme = lightColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.primary.copy(alpha = 0.1f),
    onPrimaryContainer = accent.primary,

    secondary = accent.primaryVariant,
    onSecondary = accent.onPrimary,
    secondaryContainer = accent.primaryVariant.copy(alpha = 0.1f),
    onSecondaryContainer = accent.primaryVariant,

    tertiary = AccentPurple,
    onTertiary = Color.White,
    tertiaryContainer = AccentPurple.copy(alpha = 0.1f),
    onTertiaryContainer = AccentPurple,

    background = LightBackground,
    onBackground = TextPrimaryLight,

    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE0E0E0),

    error = Color(0xFFB00020),
    onError = Color.White,

    outline = Color(0xFFDDDDDD),
    outlineVariant = Color(0xFFEEEEEE),

    inverseSurface = Color(0xFF121212),
    inverseOnSurface = Color.White,
    inversePrimary = accent.primary,

    scrim = Color.Black.copy(alpha = 0.4f)
)

@Composable
fun ZimbaBeatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.GREEN,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        createDarkColorScheme(accentColor)
    } else {
        createLightColorScheme(accentColor)
    }

    // Configure status bar and navigation bar appearance (light/dark icons)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use WindowInsetsController for modern API - colors handled by edge-to-edge
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
