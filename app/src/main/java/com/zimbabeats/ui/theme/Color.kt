package com.zimbabeats.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// ZimbaBeats Design System - Material 3 Theme
// Modern, clean, music-app inspired
// ============================================

// ============================================
// Primary Brand Colors
// ============================================
val Primary = Color(0xFF1DB954)           // Spotify Green
val PrimaryDark = Color(0xFF1AA34A)       // Darker Green
val PrimaryLight = Color(0xFF1ED760)      // Lighter Green
val OnPrimary = Color(0xFF000000)         // Black on primary

// ============================================
// Secondary Colors - Purple accent
// ============================================
val Secondary = Color(0xFF8B5CF6)         // Vibrant Purple
val SecondaryDark = Color(0xFF7C3AED)
val SecondaryLight = Color(0xFFA78BFA)
val OnSecondary = Color(0xFFFFFFFF)

// ============================================
// Tertiary Colors - Teal/Cyan
// ============================================
val Tertiary = Color(0xFF06B6D4)          // Cyan
val TertiaryDark = Color(0xFF0891B2)
val TertiaryLight = Color(0xFF22D3EE)
val OnTertiary = Color(0xFF000000)

// ============================================
// Dark Theme Colors - True Black (AMOLED)
// ============================================
val DarkBackground = Color(0xFF000000)     // Pure black
val DarkSurface = Color(0xFF121212)        // Spotify-style surface
val DarkSurfaceVariant = Color(0xFF1E1E1E) // Elevated surface
val DarkSurfaceContainer = Color(0xFF242424)
val DarkSurfaceContainerHigh = Color(0xFF2A2A2A)
val DarkSurfaceContainerHighest = Color(0xFF333333)

// Alternative: Slightly blue-tinted dark (current)
val DarkBackgroundBlue = Color(0xFF0A0A0F)
val DarkSurfaceBlue = Color(0xFF121218)
val DarkSurfaceVariantBlue = Color(0xFF1A1A22)

// ============================================
// Light Theme Colors
// ============================================
val LightBackground = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F0F0)
val LightSurfaceContainer = Color(0xFFE8E8E8)

// ============================================
// Text Colors
// ============================================
val TextPrimaryDark = Color(0xFFFFFFFF)    // White
val TextSecondaryDark = Color(0xFFB3B3B3)  // Spotify gray
val TextTertiaryDark = Color(0xFF727272)   // Dimmed
val TextPrimaryLight = Color(0xFF000000)   // Black
val TextSecondaryLight = Color(0xFF6B6B6B) // Gray

// ============================================
// Semantic Colors
// ============================================
val Success = Color(0xFF22C55E)            // Green
val Error = Color(0xFFEF4444)              // Red
val Warning = Color(0xFFF59E0B)            // Amber
val Info = Color(0xFF3B82F6)               // Blue

// ============================================
// Accent Colors - Vibrant for UI elements
// ============================================
val AccentRed = Color(0xFFEF4444)          // For favorites/hearts
val AccentPink = Color(0xFFEC4899)         // Pink
val AccentPurple = Color(0xFF8B5CF6)       // Purple
val AccentBlue = Color(0xFF3B82F6)         // Blue
val AccentCyan = Color(0xFF06B6D4)         // Cyan
val AccentTeal = Color(0xFF14B8A6)         // Teal
val AccentGreen = Color(0xFF22C55E)        // Green
val AccentYellow = Color(0xFFFBBF24)       // Yellow
val AccentOrange = Color(0xFFF97316)       // Orange

// ============================================
// Card & Surface Colors (for components)
// ============================================
val CardDark = Color(0xFF181818)           // Spotify card
val CardDarkElevated = Color(0xFF282828)   // Elevated card
val CardDarkHover = Color(0xFF2A2A2A)      // Hover state

// ============================================
// Gradient Colors
// ============================================
val GradientPurple = Color(0xFF667EEA)
val GradientPink = Color(0xFFF093FB)
val GradientBlue = Color(0xFF4FACFE)
val GradientCyan = Color(0xFF00F2FE)
val GradientGreen = Color(0xFF38EF7D)

// ============================================
// Player Colors
// ============================================
val PlayerBackground = Color(0xFF121212)
val PlayerSurface = Color(0xFF1A1A1A)
val PlayerProgress = Primary
val PlayerProgressBackground = Color(0xFF404040)

// ============================================
// Navigation Colors
// ============================================
val NavBarBackground = Color(0xFF000000)
val NavBarIndicator = Primary.copy(alpha = 0.2f)
val NavBarSelected = Color(0xFFFFFFFF)
val NavBarUnselected = Color(0xFF727272)

// ============================================
// Mini Player Colors
// ============================================
val MiniPlayerBackground = Color(0xFF282828)
val MiniPlayerBorder = Color(0xFF404040)

// ============================================
// Shimmer Colors
// ============================================
val ShimmerBackground = Color(0xFF1A1A1A)
val ShimmerHighlight = Color(0xFF2A2A2A)
val ShimmerBackgroundLight = Color(0xFFE0E0E0)
val ShimmerHighlightLight = Color(0xFFF0F0F0)

// ============================================
// Category Colors (for playlists/sections)
// ============================================
val CategoryMusic = Color(0xFF8B5CF6)
val CategoryVideos = Color(0xFF06B6D4)
val CategoryDownloads = Color(0xFFF59E0B)
val CategoryHistory = Color(0xFF3B82F6)
val CategoryFavorites = Color(0xFFEF4444)

// ============================================
// Legacy compatibility
// ============================================
val SpotifyGreen = Primary
val SpotifyGreenDark = PrimaryDark
val YouTubeMusicRed = Color(0xFFFF0000)

// Glass effects (keeping for compatibility)
val GlassWhite = Color(0x14FFFFFF)
val GlassWhiteLight = Color(0x1FFFFFFF)
val GlassWhiteMedium = Color(0x2BFFFFFF)
val GlassWhiteStrong = Color(0x3DFFFFFF)
val GlassWhiteSolid = Color(0x52FFFFFF)
val GlassBorder = Color(0x40FFFFFF)
val GlassBorderLight = Color(0x26FFFFFF)
val GlassBorderStrong = Color(0x66FFFFFF)
val DarkCard = CardDark

// Legacy accent colors
val Accent = AccentPink
val AccentDark = Color(0xFFCC1076)
val AccentLight = Color(0xFFFF4DAF)
val AccentPrimary = Primary
val AccentSecondary = AccentOrange
val AccentTertiary = AccentGreen

// Legacy kids colors
val KidsPrimary = Primary
val KidsSecondary = AccentOrange
val KidsAccent = AccentGreen
val KidsYellow = AccentYellow
val KidsGreen = Primary

// Card overlay gradient
val CardOverlayGradient = listOf(
    Color.Transparent,
    Color.Black.copy(alpha = 0.3f),
    Color.Black.copy(alpha = 0.7f)
)

// Legacy purples
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
