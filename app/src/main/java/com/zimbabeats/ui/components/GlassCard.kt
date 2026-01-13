package com.zimbabeats.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zimbabeats.ui.theme.GlassBorder
import com.zimbabeats.ui.theme.GlassWhite
import com.zimbabeats.ui.theme.GlassWhiteMedium
import com.zimbabeats.ui.theme.GlassWhiteStrong

/**
 * Glassmorphism Card Component
 * Creates a frosted glass effect with translucent background and subtle border
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    glassOpacity: GlassOpacity = GlassOpacity.Medium,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor = when (glassOpacity) {
        GlassOpacity.Light -> GlassWhite
        GlassOpacity.Medium -> GlassWhiteMedium
        GlassOpacity.Strong -> GlassWhiteStrong
    }

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = borderWidth,
                color = GlassBorder,
                shape = shape
            )
    ) {
        content()
    }
}

/**
 * Glassmorphism Card with gradient background
 */
@Composable
fun GlassGradientCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    gradientColors: List<Color> = listOf(
        Color(0x33667EEA),  // Blue with transparency
        Color(0x33764BA2)   // Purple with transparency
    ),
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(gradientColors)
            )
            .border(
                width = borderWidth,
                color = GlassBorder,
                shape = shape
            )
    ) {
        content()
    }
}

/**
 * Glass Surface - simpler version for backgrounds
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        glassOpacity = GlassOpacity.Light,
        borderWidth = 0.5.dp,
        content = content
    )
}

/**
 * Glass Button background
 */
@Composable
fun GlassButton(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        cornerRadius = 24.dp,
        glassOpacity = GlassOpacity.Medium,
        content = content
    )
}

enum class GlassOpacity {
    Light,   // 10% opacity - subtle
    Medium,  // 20% opacity - standard
    Strong   // 30% opacity - more visible
}
