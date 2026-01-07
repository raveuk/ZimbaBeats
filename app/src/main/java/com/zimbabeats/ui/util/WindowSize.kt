package com.zimbabeats.ui.util

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal to provide WindowSizeClass throughout the app
 */
val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> {
    error("WindowSizeClass not provided")
}

/**
 * Utility object for adaptive layout calculations based on window size
 */
object WindowSizeUtil {

    /**
     * Determines if the current window width is compact (phone portrait)
     */
    @Composable
    fun isCompactWidth(): Boolean {
        val windowSizeClass = LocalWindowSizeClass.current
        return windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    }

    /**
     * Determines if the current window width is medium (phone landscape, small tablet)
     */
    @Composable
    fun isMediumWidth(): Boolean {
        val windowSizeClass = LocalWindowSizeClass.current
        return windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
    }

    /**
     * Determines if the current window width is expanded (tablet, desktop)
     */
    @Composable
    fun isExpandedWidth(): Boolean {
        val windowSizeClass = LocalWindowSizeClass.current
        return windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    }

    /**
     * Returns true if the screen should use tablet layouts
     */
    @Composable
    fun isTablet(): Boolean {
        return isMediumWidth() || isExpandedWidth()
    }

    /**
     * Get the number of columns for grid layouts based on screen width
     */
    @Composable
    fun getGridColumns(): Int {
        val windowSizeClass = LocalWindowSizeClass.current
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 2
            WindowWidthSizeClass.Medium -> 3
            WindowWidthSizeClass.Expanded -> 4
            else -> 2
        }
    }

    /**
     * Get adaptive card width for horizontal scrolling sections
     */
    @Composable
    fun getCardWidth(): Dp {
        val windowSizeClass = LocalWindowSizeClass.current
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 140.dp
            WindowWidthSizeClass.Medium -> 160.dp
            WindowWidthSizeClass.Expanded -> 180.dp
            else -> 140.dp
        }
    }

    /**
     * Get adaptive card width for video cards
     */
    @Composable
    fun getVideoCardWidth(): Dp {
        val windowSizeClass = LocalWindowSizeClass.current
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 180.dp
            WindowWidthSizeClass.Medium -> 220.dp
            WindowWidthSizeClass.Expanded -> 260.dp
            else -> 180.dp
        }
    }

    /**
     * Get quick pick item width
     */
    @Composable
    fun getQuickPickWidth(): Dp {
        val windowSizeClass = LocalWindowSizeClass.current
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 200.dp
            WindowWidthSizeClass.Medium -> 240.dp
            WindowWidthSizeClass.Expanded -> 280.dp
            else -> 200.dp
        }
    }

    /**
     * Get maximum content width for centered layouts (onboarding, settings)
     */
    @Composable
    fun getMaxContentWidth(): Dp {
        val windowSizeClass = LocalWindowSizeClass.current
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> Dp.Unspecified
            WindowWidthSizeClass.Medium -> 600.dp
            WindowWidthSizeClass.Expanded -> 700.dp
            else -> Dp.Unspecified
        }
    }

    /**
     * Get horizontal padding for content based on screen size
     */
    @Composable
    fun getHorizontalPadding(): Dp {
        val windowSizeClass = LocalWindowSizeClass.current
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 16.dp
            WindowWidthSizeClass.Medium -> 24.dp
            WindowWidthSizeClass.Expanded -> 32.dp
            else -> 16.dp
        }
    }

    /**
     * Get icon size for onboarding/empty states
     */
    @Composable
    fun getLargeIconSize(): Dp {
        val windowSizeClass = LocalWindowSizeClass.current
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 80.dp
            WindowWidthSizeClass.Medium -> 100.dp
            WindowWidthSizeClass.Expanded -> 120.dp
            else -> 80.dp
        }
    }
}
