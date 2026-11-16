package com.zimbabeats.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL
import platform.UIKit.UIDevice

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformVideoPlayer(
    streamUrl: String,
    modifier: Modifier
) {
    val player = remember { AVPlayer() }
    val playerViewController = remember {
        AVPlayerViewController().apply {
            this.player = player
            this.showsPlaybackControls = true
        }
    }

    DisposableEffect(streamUrl) {
        val nsUrl = NSURL.URLWithString(streamUrl)
        if (nsUrl != null) {
            val item = AVPlayerItem(uRL = nsUrl)
            player.replaceCurrentItemWithPlayerItem(item)
            player.play()
        }
        onDispose {
            player.pause()
        }
    }

    UIKitView(
        factory = { playerViewController.view },
        modifier = modifier
    )
}

actual fun getPlatformName(): String =
    UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
