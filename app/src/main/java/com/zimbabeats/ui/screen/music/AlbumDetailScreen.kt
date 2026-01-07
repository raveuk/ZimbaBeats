package com.zimbabeats.ui.screen.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zimbabeats.core.domain.model.music.Album
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.media.music.MusicPlaybackManager
import com.zimbabeats.ui.viewmodel.music.AlbumDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onNavigateBack: () -> Unit,
    onTrackClick: (String) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit = { _, _ -> },  // Play tracks with queue
    onArtistClick: (String) -> Unit = {},
    viewModel: AlbumDetailViewModel = koinViewModel { parametersOf(albumId) },
    musicPlaybackManager: MusicPlaybackManager = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by musicPlaybackManager.playbackState.collectAsState()
    val currentPlayingTrackId = playbackState.currentTrack?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.album?.title ?: "Album") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error ?: "Unknown error",
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.album != null -> {
                    AlbumContent(
                        album = uiState.album!!,
                        currentPlayingTrackId = currentPlayingTrackId,
                        isPlaying = playbackState.isPlaying,
                        onTrackClick = { index ->
                            // Play from this track with the album as the queue
                            onPlayTracks(uiState.album!!.tracks, index)
                        },
                        onArtistClick = onArtistClick,
                        onPlayAll = {
                            // Play all tracks starting from the first
                            if (uiState.album!!.tracks.isNotEmpty()) {
                                onPlayTracks(uiState.album!!.tracks, 0)
                            }
                        },
                        onShuffle = {
                            // Shuffle play - start at a random track
                            val tracks = uiState.album!!.tracks
                            if (tracks.isNotEmpty()) {
                                val shuffledTracks = tracks.shuffled()
                                onPlayTracks(shuffledTracks, 0)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumContent(
    album: Album,
    currentPlayingTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (Int) -> Unit,  // Now takes index instead of trackId
    onArtistClick: (String) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.background
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Album header with artwork
        item {
            AlbumHeader(
                album = album,
                onArtistClick = onArtistClick,
                onPlayAll = onPlayAll,
                onShuffle = onShuffle,
                gradientColors = gradientColors
            )
        }

        // Track list section header
        if (album.tracks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Songs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${album.tracks.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Track list with track numbers
        itemsIndexed(album.tracks, key = { _, track -> track.id }) { index, track ->
            val isCurrentTrack = track.id == currentPlayingTrackId
            AlbumTrackItem(
                track = track,
                trackNumber = index + 1,
                isCurrentlyPlaying = isCurrentTrack,
                isPlaying = isCurrentTrack && isPlaying,
                onClick = { onTrackClick(index) }  // Pass index for queue playback
            )
        }

        // Empty state if no tracks
        if (album.tracks.isEmpty()) {
            item {
                Text(
                    text = "No songs available for this album",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    album: Album,
    onArtistClick: (String) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    gradientColors: List<androidx.compose.ui.graphics.Color>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(gradientColors))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Album artwork (square, not circle like artist)
        AsyncImage(
            model = album.thumbnailUrl,
            contentDescription = "Album art for ${album.title}",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Album title
        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Artist name (clickable)
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                album.artistId?.let { onArtistClick(it) }
            }
        )

        // Year if available
        album.year?.let { year ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Album stats
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${album.tracks.size} songs • ${formatTotalDuration(album.tracks)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Play and Shuffle buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Play All button
            Button(
                onClick = onPlayAll,
                enabled = album.tracks.isNotEmpty()
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play All")
            }

            // Shuffle button
            OutlinedButton(
                onClick = onShuffle,
                enabled = album.tracks.isNotEmpty()
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
    }
}

@Composable
private fun AlbumTrackItem(
    track: Track,
    trackNumber: Int,
    isCurrentlyPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrentlyPlaying)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else
                    MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Track number or playing indicator
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrentlyPlaying) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Now playing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = trackNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Track thumbnail (small)
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Show artist only if different from album artist
            if (track.artistName.isNotEmpty()) {
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Duration
        Text(
            text = formatDuration(track.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Play icon (show pause if currently playing)
        Icon(
            if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
            contentDescription = if (isCurrentlyPlaying) "Now playing ${track.title}" else "Play ${track.title}",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatTotalDuration(tracks: List<Track>): String {
    val totalMillis = tracks.sumOf { it.duration }
    val totalMinutes = totalMillis / 1000 / 60
    return if (totalMinutes >= 60) {
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        "${hours}h ${mins}m"
    } else {
        "${totalMinutes} min"
    }
}
