package com.zimbabeats.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// Spotify/YouTube Music Inspired Color Palette
// ============================================

// Primary Brand Colors
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyGreenDark = Color(0xFF1AA34A)
val YouTubeMusicRed = Color(0xFFFF0000)

// Dark Theme Base Colors (Spotify-inspired deep blacks)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF181818)
val DarkSurfaceVariant = Color(0xFF282828)
val DarkSurfaceElevated = Color(0xFF333333)
val DarkCard = Color(0xFF1E1E1E)

// Light Theme Base Colors
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF5F5F5)
val LightCard = Color(0xFFFFFFFF)

// Text Colors
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFFB3B3B3)
val TextTertiaryDark = Color(0xFF6A6A6A)
val TextPrimaryLight = Color(0xFF121212)
val TextSecondaryLight = Color(0xFF535353)

// Accent Colors - Vibrant & Kid-Friendly
val AccentPrimary = Color(0xFF1DB954)     // Spotify Green
val AccentSecondary = Color(0xFFFF6B6B)   // Coral Red
val AccentTertiary = Color(0xFF4ECDC4)    // Teal
val AccentPurple = Color(0xFF8B5CF6)      // Vibrant Purple
val AccentPink = Color(0xFFEC4899)        // Hot Pink
val AccentOrange = Color(0xFFFF8C00)      // Orange
val AccentYellow = Color(0xFFFFD700)      // Gold
val AccentBlue = Color(0xFF3B82F6)        // Sky Blue

// Gradient Colors
val GradientStart = Color(0xFF1DB954)
val GradientMiddle = Color(0xFF1ED760)
val GradientEnd = Color(0xFF4ECDC4)

// Kids-Friendly Category Colors
val CategoryMusic = Color(0xFF8B5CF6)
val CategoryEducation = Color(0xFF3B82F6)
val CategoryAnimations = Color(0xFFEC4899)
val CategoryGames = Color(0xFFFF6B6B)
val CategoryStories = Color(0xFFFFD700)

// Shimmer Colors
val ShimmerBackground = Color(0xFF282828)
val ShimmerHighlight = Color(0xFF3D3D3D)
val ShimmerBackgroundLight = Color(0xFFE0E0E0)
val ShimmerHighlightLight = Color(0xFFF5F5F5)

// Player Colors
val PlayerBackground = Color(0xFF0D0D0D)
val PlayerSurface = Color(0xFF1A1A1A)
val PlayerProgress = AccentPrimary
val PlayerProgressBackground = Color(0xFF404040)

// Mini Player
val MiniPlayerBackground = Color(0xFF282828)
val MiniPlayerBorder = Color(0xFF404040)

// Navigation Bar
val NavBarBackground = Color(0xFF121212)
val NavBarIndicator = AccentPrimary.copy(alpha = 0.2f)
val NavBarSelectedIcon = Color.White
val NavBarUnselectedIcon = Color(0xFF7F7F7F)

// Card Overlays
val CardOverlayGradient = listOf(
    Color.Transparent,
    Color.Black.copy(alpha = 0.3f),
    Color.Black.copy(alpha = 0.7f)
)

// Legacy Colors (for compatibility)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Kids-friendly accent colors (legacy)
val KidsPrimary = AccentPrimary
val KidsSecondary = AccentSecondary
val KidsAccent = AccentTertiary
val KidsYellow = AccentYellow
val KidsGreen = AccentPrimary