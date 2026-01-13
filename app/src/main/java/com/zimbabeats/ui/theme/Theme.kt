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
 * Modern Material 3 Dark Color Scheme
 * Inspired by Spotify/Apple Music dark themes
 */
private fun createDarkColorScheme(accent: AccentColor): ColorScheme = darkColorScheme(
    // Primary - Main brand color (green by default)
    primary = accent.primary,
    onPrimary = OnPrimary,
    primaryContainer = accent.primary.copy(alpha = 0.2f),
    onPrimaryContainer = Color.White,

    // Secondary - Purple accent
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = Secondary.copy(alpha = 0.2f),
    onSecondaryContainer = Color.White,

    // Tertiary - Cyan accent
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = Tertiary.copy(alpha = 0.2f),
    onTertiaryContainer = Color.White,

    // Background - Pure black for AMOLED
    background = DarkBackground,
    onBackground = TextPrimaryDark,

    // Surface - Spotify-style elevated surfaces
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,

    // Surface containers for elevation
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceVariant,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,

    // Error
    error = Error,
    onError = Color.White,
    errorContainer = Error.copy(alpha = 0.2f),
    onErrorContainer = Error,

    // Outline
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF2A2A2A),

    // Inverse
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF121212),
    inversePrimary = PrimaryDark,

    // Scrim
    scrim = Color.Black.copy(alpha = 0.5f),

    // Surface tint
    surfaceTint = accent.primary
)

/**
 * Material 3 Light Color Scheme
 */
private fun createLightColorScheme(accent: AccentColor): ColorScheme = lightColorScheme(
    primary = accent.primary,
    onPrimary = Color.White,
    primaryContainer = accent.primary.copy(alpha = 0.15f),
    onPrimaryContainer = accent.primary,

    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = Secondary.copy(alpha = 0.15f),
    onSecondaryContainer = SecondaryDark,

    tertiary = Tertiary,
    onTertiary = Color.White,
    tertiaryContainer = Tertiary.copy(alpha = 0.15f),
    onTertiaryContainer = TertiaryDark,

    background = LightBackground,
    onBackground = TextPrimaryLight,

    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurfaceContainer,
    surfaceContainerHighest = Color(0xFFE0E0E0),

    error = Error,
    onError = Color.White,
    errorContainer = Error.copy(alpha = 0.1f),
    onErrorContainer = Error,

    outline = Color(0xFFD0D0D0),
    outlineVariant = Color(0xFFE8E8E8),

    inverseSurface = DarkSurface,
    inverseOnSurface = Color.White,
    inversePrimary = PrimaryLight,

    scrim = Color.Black.copy(alpha = 0.3f),

    surfaceTint = accent.primary
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

    // Configure status bar and navigation bar appearance
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ZimbaBeatsShapes,
        content = content
    )
}
