package com.zimbabeats.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Shapes for ZimbaBeats
 * Rounded corners following M3 guidelines
 */
val ZimbaBeatsShapes = Shapes(
    // Extra Small - chips, small badges
    extraSmall = RoundedCornerShape(4.dp),

    // Small - buttons, text fields
    small = RoundedCornerShape(8.dp),

    // Medium - cards, dialogs
    medium = RoundedCornerShape(16.dp),

    // Large - bottom sheets, navigation drawers
    large = RoundedCornerShape(24.dp),

    // Extra Large - full screen dialogs
    extraLarge = RoundedCornerShape(32.dp)
)

// Custom shapes for specific components
object ZimbaBeatsCorners {
    val ExtraSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val ExtraLarge = RoundedCornerShape(24.dp)
    val Full = RoundedCornerShape(50)  // Pill shape

    // Component-specific
    val Card = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(8.dp)
    val TextField = RoundedCornerShape(12.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val MiniPlayer = RoundedCornerShape(16.dp)
    val AlbumArt = RoundedCornerShape(12.dp)
    val Thumbnail = RoundedCornerShape(8.dp)
    val NavigationBar = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}
