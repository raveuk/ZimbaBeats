package com.zimbabeats.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.ui.components.EmptyState
import com.zimbabeats.ui.components.PlaylistDetailShimmer
import com.zimbabeats.ui.viewmodel.PlaylistDetailViewModel
import com.zimbabeats.ui.viewmodel.PlaylistSharingViewModel
import com.zimbabeats.ui.viewmodel.ShareState
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onNavigateBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onTrackClick: (String) -> Unit = {},
    viewModel: PlaylistDetailViewModel = koinViewModel { parametersOf(playlistId) },
    sharingViewModel: PlaylistSharingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val shareState by sharingViewModel.shareState.collectAsState()
    val isSharingEnabled by sharingViewModel.isSharingEnabled.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.playlist?.name ?: "Playlist",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Share button (only if linked to family)
                    if (isSharingEnabled && uiState.playlist != null && !uiState.playlist!!.isImported) {
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(
                                if (uiState.playlist?.isShared == true) Icons.Default.Link else Icons.Default.Share,
                                "Share Playlist"
                            )
                        }
                    }
                    if (uiState.videos.isNotEmpty() || uiState.tracks.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.ClearAll, "Clear Playlist")
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete Playlist")
                    }
                }
            )
        }
    ) { paddingValues ->
        Crossfade(
            targetState = uiState.isLoading,
            label = "playlist_detail_content",
            modifier = Modifier.padding(paddingValues)
        ) { isLoading ->
            when {
                isLoading -> {
                    PlaylistDetailShimmer(
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.playlist == null -> {
                    EmptyState(
                        icon = {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = "Playlist not found",
                        subtitle = "This playlist may have been deleted",
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            Button(onClick = onNavigateBack) {
                                Text("Go Back")
                            }
                        }
                    )
                }
                else -> {
                    PlaylistDetailContent(
                        playlist = uiState.playlist!!,
                        videos = uiState.videos,
                        tracks = uiState.tracks,
                        onVideoClick = onVideoClick,
                        onTrackClick = onTrackClick,
                        onRemoveVideo = { viewModel.removeVideo(it) },
                        onRemoveTrack = { viewModel.removeTrack(it) }
                    )
                }
            }
        }
    }

    // Delete playlist confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete \"${uiState.playlist?.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist { onNavigateBack() }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear playlist confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.ClearAll, contentDescription = null) },
            title = { Text("Clear Playlist") },
            text = { Text("Remove all ${uiState.videos.size + uiState.tracks.size} items from this playlist?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPlaylist()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Share playlist dialog
    if (showShareDialog && uiState.playlist != null) {
        SharePlaylistDialog(
            playlist = uiState.playlist!!,
            shareCode = uiState.playlist?.shareCode ?: when (shareState) {
                is ShareState.Success -> (shareState as ShareState.Success).shareCode
                else -> null
            },
            isLoading = shareState is ShareState.Loading,
            isGenerating = shareState is ShareState.Generating,
            videoCount = uiState.videos.size,
            trackCount = uiState.tracks.size,
            errorMessage = when (shareState) {
                is ShareState.Error -> (shareState as ShareState.Error).message
                else -> null
            },
            onGenerateCode = {
                sharingViewModel.generateShareCode(
                    playlist = uiState.playlist!!,
                    videos = uiState.videos,
                    tracks = uiState.tracks
                )
            },
            onRevokeCode = {
                sharingViewModel.revokeShareCode(uiState.playlist!!)
                showShareDialog = false
            },
            onDismiss = {
                showShareDialog = false
                sharingViewModel.resetShareState()
            }
        )
    }
}

@Composable
private fun PlaylistDetailContent(
    playlist: com.zimbabeats.core.domain.model.Playlist,
    videos: List<Video>,
    tracks: List<Track>,
    onVideoClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onRemoveVideo: (String) -> Unit,
    onRemoveTrack: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Playlist Header
        PlaylistHeader(
            name = playlist.name,
            description = playlist.description,
            videoCount = videos.size,
            trackCount = tracks.size,
            colorHex = playlist.color.hex
        )

        if (videos.isEmpty() && tracks.isEmpty()) {
            EmptyState(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                title = "No content in this playlist",
                subtitle = "Add videos or music from the player",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Music tracks section
                if (tracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Music (${tracks.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(tracks, key = { "track_${it.id}" }) { track ->
                        var showRemoveConfirmation by remember { mutableStateOf(false) }

                        PlaylistTrackItem(
                            track = track,
                            onClick = { onTrackClick(track.id) },
                            onRemove = { showRemoveConfirmation = true }
                        )

                        if (showRemoveConfirmation) {
                            AlertDialog(
                                onDismissRequest = { showRemoveConfirmation = false },
                                title = { Text("Remove from playlist?") },
                                text = { Text("Remove \"${track.title}\" from this playlist?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            onRemoveTrack(track.id)
                                            showRemoveConfirmation = false
                                        }
                                    ) {
                                        Text("Remove")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRemoveConfirmation = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }

                // Videos section
                if (videos.isNotEmpty()) {
                    item {
                        Text(
                            text = "Videos (${videos.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(videos, key = { "video_${it.id}" }) { video ->
                        PlaylistVideoItem(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            onRemove = { onRemoveVideo(video.id) }
                        )
                    }
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    name: String,
    description: String?,
    videoCount: Int,
    trackCount: Int = 0,
    colorHex: String
) {
    val color = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist icon
            Surface(
                modifier = Modifier.size(80.dp),
                color = color,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                description?.let {
                    if (it.isNotBlank()) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (trackCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$trackCount ${if (trackCount == 1) "song" else "songs"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (videoCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$videoCount ${if (videoCount == 1) "video" else "videos"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistVideoItem(
    video: Video,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(120.dp, 68.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Play icon overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxSize(),
                        tint = Color.White
                    )
                }

                // Duration badge
                if (video.duration > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = formatDuration(video.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Video info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            ) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    "Remove from playlist"
                )
            }
        }
    }
}

@Composable
private fun PlaylistTrackItem(
    track: Track,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Music icon overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "Music",
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxSize(),
                        tint = Color.White
                    )
                }
            }

            // Track info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            if (track.duration > 0) {
                Text(
                    text = formatTrackDuration(track.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            ) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    "Remove from playlist"
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

private fun formatTrackDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
