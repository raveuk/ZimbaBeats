package com.zimbabeats.shared.player

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun PlatformVideoPlayer(
    streamUrl: String,
    modifier: Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                val mediaController = MediaController(context)
                mediaController.setAnchorView(this)
                setMediaController(mediaController)
                setVideoURI(Uri.parse(streamUrl))
                setOnPreparedListener { mp ->
                    mp.isLooping = false
                    start()
                }
            }
        },
        update = { videoView ->
            if (videoView.tag != streamUrl) {
                videoView.tag = streamUrl
                videoView.setVideoURI(Uri.parse(streamUrl))
                videoView.start()
            }
        }
    )
}

actual fun getPlatformName(): String = "Android"
