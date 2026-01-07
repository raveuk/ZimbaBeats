package com.zimbabeats.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zimbabeats.media.controller.MediaControllerManager
import com.zimbabeats.media.queue.RepeatMode
import org.koin.compose.koinInject

/**
 * Full playback controls with shuffle, repeat, and queue access
 */
@Composable
fun FullPlaybackControls(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
    mediaController: MediaControllerManager = koinInject()
) {
    val playbackState by mediaController.playbackState.collectAsState()
    val shuffleEnabled by mediaController.playbackQueue.shuffleEnabled.collectAsState()
    val repeatMode by mediaController.playbackQueue.repeatMode.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle button
        IconButton(
            onClick = { mediaController.toggleShuffle() }
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else tintColor.copy(alpha = 0.5f)
            )
        }

        // Previous button
        IconButton(
            onClick = { mediaController.seekToPrevious() }
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = tintColor
            )
        }

        // Seek back button
        IconButton(
            onClick = { mediaController.seekTo(mediaController.getCurrentPosition() - 10_000) }
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = "Seek back 10s",
                tint = tintColor
            )
        }

        // Play/Pause button (larger)
        FilledIconButton(
            onClick = {
                if (playbackState.isPlaying) {
                    mediaController.pause()
                } else {
                    mediaController.play()
                }
            },
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = if (playbackState.isPlaying) {
                    Icons.Default.Pause
                } else {
                    Icons.Default.PlayArrow
                },
                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(36.dp)
            )
        }

        // Seek forward button
        IconButton(
            onClick = { mediaController.seekTo(mediaController.getCurrentPosition() + 10_000) }
        ) {
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = "Seek forward 10s",
                tint = tintColor
            )
        }

        // Next button
        IconButton(
            onClick = { mediaController.seekToNext() }
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = tintColor
            )
        }

        // Repeat button
        IconButton(
            onClick = { mediaController.cycleRepeatMode() }
        ) {
            val (icon, tint) = when (repeatMode) {
                RepeatMode.OFF -> Icons.Default.Repeat to tintColor.copy(alpha = 0.5f)
                RepeatMode.ALL -> Icons.Default.Repeat to MaterialTheme.colorScheme.primary
                RepeatMode.ONE -> Icons.Default.RepeatOne to MaterialTheme.colorScheme.primary
            }
            Icon(
                imageVector = icon,
                contentDescription = "Repeat: $repeatMode",
                tint = tint
            )
        }
    }
}

/**
 * Seekbar with time display
 */
@Composable
fun VideoSeekBar(
    modifier: Modifier = Modifier,
    mediaController: MediaControllerManager = koinInject()
) {
    val playbackState by mediaController.playbackState.collectAsState()

    val position = playbackState.currentPosition
    val duration = playbackState.duration

    val progress = if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Slider(
            value = progress,
            onValueChange = { newProgress ->
                val newPosition = (newProgress * duration).toLong()
                mediaController.seekTo(newPosition)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(position),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Format time in milliseconds to MM:SS or HH:MM:SS
 */
private fun formatTime(timeMs: Long): String {
    if (timeMs <= 0) return "0:00"

    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
