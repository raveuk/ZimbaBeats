package com.zimbabeats.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Multiplatform Video/Audio surface that uses AVPlayerViewController on iOS
 * and Media3 / native playback on Android.
 */
@Composable
expect fun PlatformVideoPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier
)

expect fun getPlatformName(): String
