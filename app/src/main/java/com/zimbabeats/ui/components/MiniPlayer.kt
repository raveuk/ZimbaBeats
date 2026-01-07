package com.zimbabeats.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zimbabeats.media.controller.MediaControllerManager
import com.zimbabeats.media.music.MusicPlaybackManager
import com.zimbabeats.media.queue.QueueItem
import com.zimbabeats.ui.accessibility.ContentDescriptions
import com.zimbabeats.ui.accessibility.formatDurationForAccessibility
import com.zimbabeats.ui.accessibility.formatProgressForAccessibility
import com.zimbabeats.ui.theme.*
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Spotify-inspired mini player that appears at the bottom of the screen.
 * Supports both video and music playback - music takes priority when playing.
 */
@Composable
fun MiniPlayer(
    onExpand: (String) -> Unit,
    onMusicExpand: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    mediaController: MediaControllerManager = koinInject(),
    musicPlaybackManager: MusicPlaybackManager = koinInject()
) {
    // Connect to media service on first composition (lazy initialization)
    LaunchedEffect(Unit) {
        mediaController.connect()
    }

    // Video playback state
    val currentItem by mediaController.playbackQueue.queue.collectAsState()
    val currentIndex by mediaController.playbackQueue.currentIndex.collectAsState()
    val videoPlaybackState by mediaController.playbackState.collectAsState()
    val video = currentItem.getOrNull(currentIndex)

    // Music playback state
    val musicPlaybackState by musicPlaybackManager.playbackState.collectAsState()
    val currentTrack = musicPlaybackState.currentTrack

    // Determine which content to show (music takes priority when actively playing)
    val showMusicMiniPlayer = currentTrack != null && (musicPlaybackState.isPlaying || !videoPlaybackState.isPlaying)
    val showVideoMiniPlayer = video != null && !showMusicMiniPlayer

    // Music Mini Player
    AnimatedVisibility(
        visible = showMusicMiniPlayer && currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier.semantics {
            isTraversalGroup = true
            traversalIndex = 100f
        }
    ) {
        currentTrack?.let { track ->
            val playbackStateDesc = if (musicPlaybackState.isPlaying) "Playing" else "Paused"
            val progressPercent = if (musicPlaybackState.duration > 0) {
                ((musicPlaybackState.currentPosition.toFloat() / musicPlaybackState.duration) * 100).toInt()
            } else 0
            val miniPlayerDescription = "Music mini player: ${track.title} by ${track.artistName}. $playbackStateDesc, $progressPercent percent complete. Double tap to expand."

            Column {
                // Progress bar
                MusicMiniPlayerProgress(
                    playbackState = musicPlaybackState,
                    modifier = Modifier.clearAndSetSemantics { }
                )

                // Main content
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = miniPlayerDescription
                            role = Role.Button
                        }
                        .clickable { onMusicExpand(track.id) },
                    color = MiniPlayerBackground,
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Music note badge on thumbnail
                        Box {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            // Music indicator badge
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .padding(2.dp),
                                tint = Color.White
                            )
                        }

                        // Track info
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White
                            )
                            Text(
                                text = track.artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Playback controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val playPauseInteraction = remember { MutableInteractionSource() }
                            val isPlayPausePressed by playPauseInteraction.collectIsPressedAsState()
                            val playPauseScale by animateFloatAsState(
                                targetValue = if (isPlayPausePressed) 0.85f else 1f,
                                label = "play_scale"
                            )

                            IconButton(
                                onClick = {
                                    if (musicPlaybackState.isPlaying) {
                                        musicPlaybackManager.pause()
                                    } else {
                                        musicPlaybackManager.play()
                                    }
                                },
                                modifier = Modifier.scale(playPauseScale),
                                interactionSource = playPauseInteraction
                            ) {
                                Icon(
                                    imageVector = if (musicPlaybackState.isPlaying) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = if (musicPlaybackState.isPlaying)
                                        "Pause music"
                                    else
                                        "Play music",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = { musicPlaybackManager.skipToNext() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next track",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Video Mini Player (shown only when no music is playing)
    AnimatedVisibility(
        visible = showVideoMiniPlayer && video != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier.semantics {
            isTraversalGroup = true
            traversalIndex = 100f
        }
    ) {
        video?.let { queueItem ->
            val playbackStateDesc = if (videoPlaybackState.isPlaying) "Playing" else "Paused"
            val progressPercent = if (videoPlaybackState.duration > 0) {
                ((videoPlaybackState.currentPosition.toFloat() / videoPlaybackState.duration) * 100).toInt()
            } else 0
            val miniPlayerDescription = "Mini player: ${queueItem.video.title} by ${queueItem.video.channelName}. $playbackStateDesc, $progressPercent percent complete. Double tap to expand."

            Column {
                MiniPlayerProgress(
                    mediaController = mediaController,
                    modifier = Modifier.clearAndSetSemantics { }
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = miniPlayerDescription
                            role = Role.Button
                        }
                        .clickable { onExpand(queueItem.video.id) },
                    color = MiniPlayerBackground,
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = queueItem.video.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = queueItem.video.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White
                            )
                            Text(
                                text = queueItem.video.channelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val playPauseInteraction = remember { MutableInteractionSource() }
                            val isPlayPausePressed by playPauseInteraction.collectIsPressedAsState()
                            val playPauseScale by animateFloatAsState(
                                targetValue = if (isPlayPausePressed) 0.85f else 1f,
                                label = "play_scale"
                            )

                            IconButton(
                                onClick = {
                                    if (videoPlaybackState.isPlaying) {
                                        mediaController.pause()
                                    } else {
                                        mediaController.play()
                                    }
                                },
                                modifier = Modifier.scale(playPauseScale),
                                interactionSource = playPauseInteraction
                            ) {
                                Icon(
                                    imageVector = if (videoPlaybackState.isPlaying) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = if (videoPlaybackState.isPlaying)
                                        ContentDescriptions.PAUSE_VIDEO
                                    else
                                        ContentDescriptions.PLAY_VIDEO,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = { mediaController.seekToNext() }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = ContentDescriptions.NEXT_VIDEO,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Progress bar for music mini player
 */
@Composable
private fun MusicMiniPlayerProgress(
    playbackState: com.zimbabeats.media.music.MusicPlaybackState,
    modifier: Modifier = Modifier
) {
    val progress = if (playbackState.duration > 0) {
        playbackState.currentPosition.toFloat() / playbackState.duration
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 100),
        label = "progress_animation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(PlayerProgressBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                )
        )
    }
}

/**
 * Spotify-style progress bar for mini player
 */
@Composable
fun MiniPlayerProgress(
    modifier: Modifier = Modifier,
    mediaController: MediaControllerManager = koinInject()
) {
    val playbackState by mediaController.playbackState.collectAsState()

    val progress = if (playbackState.duration > 0) {
        playbackState.currentPosition.toFloat() / playbackState.duration
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 100),
        label = "progress_animation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(PlayerProgressBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                )
        )
    }
}

/**
 * Enhanced mini player with larger controls for kids
 */
@Composable
fun KidsFriendlyMiniPlayer(
    onExpand: (String) -> Unit,
    modifier: Modifier = Modifier,
    mediaController: MediaControllerManager = koinInject()
) {
    LaunchedEffect(Unit) {
        mediaController.connect()
    }

    val currentItem by mediaController.playbackQueue.queue.collectAsState()
    val currentIndex by mediaController.playbackQueue.currentIndex.collectAsState()
    val playbackState by mediaController.playbackState.collectAsState()

    val video = currentItem.getOrNull(currentIndex)

    AnimatedVisibility(
        visible = video != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier.semantics {
            // Place mini player at end of traversal order to not steal focus
            isTraversalGroup = true
            traversalIndex = 100f
        }
    ) {
        video?.let { queueItem ->
            // Build accessibility state description
            val playbackStateDesc = if (playbackState.isPlaying) "Playing" else "Paused"
            val progressPercent = if (playbackState.duration > 0) {
                ((playbackState.currentPosition.toFloat() / playbackState.duration) * 100).toInt()
            } else 0
            val miniPlayerDescription = "Mini player: ${queueItem.video.title} by ${queueItem.video.channelName}. $playbackStateDesc, $progressPercent percent complete. Double tap to expand."

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics {
                        contentDescription = miniPlayerDescription
                        role = Role.Button
                    }
                    .clickable { onExpand(queueItem.video.id) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column {
                    // Progress at top (decorative, handled by parent semantics)
                    LinearProgressIndicator(
                        progress = {
                            if (playbackState.duration > 0) {
                                playbackState.currentPosition.toFloat() / playbackState.duration
                            } else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clearAndSetSemantics { },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = PlayerProgressBackground
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Large thumbnail
                        AsyncImage(
                            model = queueItem.video.thumbnailUrl,
                            contentDescription = null, // Handled by parent semantics
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        // Info
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = queueItem.video.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White
                            )
                            Text(
                                text = queueItem.video.channelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Large play button
                        val playButtonDesc = if (playbackState.isPlaying)
                            ContentDescriptions.PAUSE_VIDEO
                        else
                            ContentDescriptions.PLAY_VIDEO

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .shadow(4.dp, CircleShape)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .semantics {
                                    contentDescription = playButtonDesc
                                    role = Role.Button
                                }
                                .clickable {
                                    if (playbackState.isPlaying) {
                                        mediaController.pause()
                                    } else {
                                        mediaController.play()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = null, // Handled by parent Box semantics
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
