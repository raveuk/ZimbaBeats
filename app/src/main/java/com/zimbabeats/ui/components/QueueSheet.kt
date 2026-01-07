package com.zimbabeats.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zimbabeats.media.controller.MediaControllerManager
import com.zimbabeats.media.queue.QueueItem
import org.koin.compose.koinInject

/**
 * Bottom sheet showing the playback queue
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    mediaController: MediaControllerManager = koinInject()
) {
    val queue by mediaController.playbackQueue.queue.collectAsState()
    val currentIndex by mediaController.playbackQueue.currentIndex.collectAsState()
    val shuffleEnabled by mediaController.playbackQueue.shuffleEnabled.collectAsState()
    val repeatMode by mediaController.playbackQueue.repeatMode.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queue (${queue.size})",
                    style = MaterialTheme.typography.titleLarge
                )

                Row {
                    // Shuffle toggle
                    IconButton(onClick = { mediaController.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // Repeat mode toggle
                    IconButton(onClick = { mediaController.cycleRepeatMode() }) {
                        val icon = when (repeatMode) {
                            com.zimbabeats.media.queue.RepeatMode.OFF -> Icons.Default.Repeat
                            com.zimbabeats.media.queue.RepeatMode.ALL -> Icons.Default.Repeat
                            com.zimbabeats.media.queue.RepeatMode.ONE -> Icons.Default.RepeatOne
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Repeat: $repeatMode",
                            tint = if (repeatMode == com.zimbabeats.media.queue.RepeatMode.OFF) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    // Clear queue
                    IconButton(
                        onClick = { mediaController.clearQueue() },
                        enabled = queue.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Clear Queue"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Queue list
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(queue) { index, item ->
                    QueueItemCard(
                        item = item,
                        isCurrentlyPlaying = index == currentIndex,
                        onClick = {
                            mediaController.playbackQueue.skipToIndex(index)
                        },
                        onRemove = {
                            mediaController.removeFromQueue(index)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: QueueItem,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Now playing indicator
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Now Playing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }

            // Thumbnail
            AsyncImage(
                model = item.video.thumbnailUrl,
                contentDescription = item.video.title,
                modifier = Modifier
                    .size(60.dp, 40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            // Video info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentlyPlaying) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = item.video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentlyPlaying) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Remove button
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from queue",
                    tint = if (isCurrentlyPlaying) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
