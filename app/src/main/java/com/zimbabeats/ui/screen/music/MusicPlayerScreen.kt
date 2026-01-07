package com.zimbabeats.ui.screen.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.media.player.PlayerState
import com.zimbabeats.ui.viewmodel.music.MusicPlayerViewModel
import com.zimbabeats.ui.viewmodel.music.RepeatMode
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    trackId: String,
    onNavigateBack: () -> Unit,
    viewModel: MusicPlayerViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()

    var showQueue by remember { mutableStateOf(false) }

    // Local state for current position (polled from player)
    var currentPosition by remember { mutableStateOf(0L) }

    // Poll current position from player - only when playing, with smoother updates
    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying) {
            // Update more frequently for smoother slider movement
            while (playerState.isPlaying) {
                currentPosition = viewModel.getPlayer().currentPosition.toLong().coerceAtLeast(0L)
                delay(250) // Update every 250ms for smooth slider
            }
        } else {
            // Update once when paused to show accurate position
            currentPosition = viewModel.getPlayer().currentPosition.toLong().coerceAtLeast(0L)
        }
    }

    // Debug logging
    android.util.Log.d("MusicPlayerScreen", "Screen opened with trackId: $trackId")
    android.util.Log.d("MusicPlayerScreen", "UI State - isLoading: ${uiState.isLoading}, error: ${uiState.error}, track: ${uiState.currentTrack?.title}")

    // Load track when screen opens
    LaunchedEffect(trackId) {
        android.util.Log.d("MusicPlayerScreen", "LaunchedEffect triggered, loading track: $trackId")
        viewModel.loadTrack(trackId)
    }

    val track = uiState.currentTrack

    // Use track's thumbnail colors for gradient background
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.background
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = track?.albumName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQueue = !showQueue }) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (showQueue) {
            // Queue view
            QueueView(
                queue = uiState.queue,
                currentIndex = uiState.currentIndex,
                onTrackClick = { index -> viewModel.skipToIndex(index) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            // Player view
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(gradientColors))
                    .padding(paddingValues)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.error != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
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
                                text = uiState.error ?: "Unknown error",
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { viewModel.loadTrack(trackId) }) {
                                Text("Retry")
                            }
                        }
                    }
                    track != null -> {
                        PlayerContent(
                            track = track,
                            playerState = playerState,
                            currentPosition = currentPosition,
                            isFavorite = uiState.isFavorite,
                            repeatMode = uiState.repeatMode,
                            shuffleEnabled = uiState.shuffleEnabled,
                            sleepTimerEnabled = uiState.sleepTimerEnabled,
                            sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                            lyrics = uiState.lyrics,
                            isLoadingLyrics = uiState.isLoadingLyrics,
                            showLyrics = uiState.showLyrics,
                            onPlayPause = {
                                if (playerState.isPlaying) viewModel.pause()
                                else viewModel.play()
                            },
                            onSkipNext = { viewModel.skipToNext() },
                            onSkipPrevious = { viewModel.skipToPrevious() },
                            onSeek = { viewModel.seekTo(it) },
                            onToggleFavorite = { viewModel.toggleFavorite() },
                            onToggleRepeat = { viewModel.toggleRepeatMode() },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onAddToPlaylist = { viewModel.showPlaylistPicker() },
                            onSleepTimerClick = { viewModel.showSleepTimerPicker() },
                            onToggleLyrics = { viewModel.toggleLyricsView() }
                        )
                    }
                    else -> {
                        // Fallback - should not reach here normally
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Loading track...",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                            CircularProgressIndicator()
                            Button(onClick = { viewModel.loadTrack(trackId) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }

    // Playlist picker dialog
    if (uiState.showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = uiState.playlists,
            onPlaylistSelected = { viewModel.addToPlaylist(it.id) },
            onCreatePlaylist = { viewModel.createPlaylistAndAddTrack(it) },
            onDismiss = { viewModel.hidePlaylistPicker() }
        )
    }

    // Sleep timer picker dialog
    if (uiState.showSleepTimerPicker) {
        SleepTimerPickerDialog(
            isTimerActive = uiState.sleepTimerEnabled,
            remainingTime = uiState.sleepTimerRemainingMs,
            onTimerSelected = { minutes -> viewModel.setSleepTimer(minutes) },
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onDismiss = { viewModel.hideSleepTimerPicker() }
        )
    }
}

@Composable
private fun PlayerContent(
    track: Track,
    playerState: PlayerState,
    currentPosition: Long,
    isFavorite: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    sleepTimerEnabled: Boolean,
    sleepTimerRemainingMs: Long,
    lyrics: com.zimbabeats.core.domain.model.music.Lyrics?,
    isLoadingLyrics: Boolean,
    showLyrics: Boolean,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onToggleLyrics: () -> Unit
) {
    // Track slider dragging state for smooth seeking
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    // Use drag position while dragging, otherwise use actual position
    val displayPosition = if (isDragging) dragPosition else currentPosition.toFloat()

    // Simple column layout - album art uses weight to fill remaining space
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .semantics { contentDescription = "Now playing ${track.title} by ${track.artistName}" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Small top spacing
        Spacer(modifier = Modifier.height(8.dp))

        // Album art or Lyrics view - takes all remaining space using weight
        Box(
            modifier = Modifier
                .weight(1f)  // Takes remaining space
                .fillMaxWidth()
                .aspectRatio(1f, matchHeightConstraintsFirst = true) // Square, but height-constrained
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleLyrics),
            contentAlignment = Alignment.Center
        ) {
            if (showLyrics) {
                LyricsView(
                    lyrics = lyrics,
                    isLoading = isLoadingLyrics,
                    currentPosition = currentPosition
                )
            } else {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = "Album art for ${track.title}. Tap to show lyrics.",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Track info - compact
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
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
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    modifier = Modifier.size(20.dp),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Progress slider - compact
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = displayPosition,
                onValueChange = { newValue ->
                    isDragging = true
                    dragPosition = newValue
                },
                onValueChangeFinished = {
                    onSeek(dragPosition.toLong())
                    isDragging = false
                },
                valueRange = 0f..playerState.duration.coerceAtLeast(1).toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(displayPosition.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(playerState.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Playback controls - compact row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.size(20.dp),
                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(28.dp)
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(56.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(
                onClick = onToggleRepeat,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = when (repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    modifier = Modifier.size(20.dp),
                    tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Additional actions row - compact
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add to playlist
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Sleep Timer with badge
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = onSleepTimerClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = "Sleep timer",
                        modifier = Modifier.size(22.dp),
                        tint = if (sleepTimerEnabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sleepTimerEnabled) {
                    Text(
                        text = formatTimerTime(sleepTimerRemainingMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 14.dp)
                    )
                }
            }
        }

        // Bottom safe area
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun formatTimerTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Lyrics display view with synced highlighting
 */
@Composable
private fun LyricsView(
    lyrics: com.zimbabeats.core.domain.model.music.Lyrics?,
    isLoading: Boolean,
    currentPosition: Long
) {
    val listState = rememberLazyListState()

    // Find current line index for synced lyrics
    val currentLineIndex = remember(lyrics, currentPosition) {
        if (lyrics?.isSynced == true) {
            lyrics.lines.indexOfLast { it.startTimeMs <= currentPosition }
                .coerceAtLeast(0)
        } else {
            -1
        }
    }

    // Auto-scroll to current line for synced lyrics
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && lyrics?.isSynced == true) {
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = -200  // Offset to center the line
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading lyrics...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            lyrics == null || lyrics.lines.isEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No lyrics available",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap to show album art",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Synced indicator badge
                    if (lyrics.isSynced) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Synced Lyrics",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 32.dp)
                    ) {
                        items(lyrics.lines.size) { index ->
                            val line = lyrics.lines[index]
                            val isCurrentLine = index == currentLineIndex && lyrics.isSynced

                            // Animated text style for current line
                            val textColor by animateColorAsState(
                                targetValue = when {
                                    isCurrentLine -> MaterialTheme.colorScheme.primary
                                    lyrics.isSynced && index < currentLineIndex ->
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                label = "lyric_color"
                            )

                            val textStyle = if (isCurrentLine) {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyLarge
                            }

                            Text(
                                text = line.text,
                                style = textStyle,
                                textAlign = TextAlign.Center,
                                color = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = if (isCurrentLine) 4.dp else 0.dp)
                            )
                        }

                        // Show lyrics source
                        lyrics.source?.let { source ->
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Lyrics by $source",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueView(
    queue: List<Track>,
    currentIndex: Int,
    onTrackClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Up Next",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            itemsIndexed(queue) { index, track ->
                val isCurrentTrack = index == currentIndex

                Surface(
                    onClick = { onTrackClick(index) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isCurrentTrack) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                           else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Track number or playing indicator
                        Box(
                            modifier = Modifier.width(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrentTrack) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = "Now playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrentTrack) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
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

                        Text(
                            text = formatTime(track.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun PlaylistPickerDialog(
    playlists: List<Playlist>,  // Unified playlists (videos + music)
    onPlaylistSelected: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName)
                            showCreateDialog = false
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add to Playlist") },
            text = {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("Create new playlist") },
                            leadingContent = {
                                Icon(Icons.Default.Add, contentDescription = null)
                            },
                            modifier = Modifier.clickable { showCreateDialog = true }
                        )
                        HorizontalDivider()
                    }

                    items(playlists.size) { index ->
                        val playlist = playlists[index]
                        // Show total items (videos + tracks) in unified playlist
                        val itemCount = playlist.totalItemCount
                        val itemLabel = when {
                            playlist.videoCount > 0 && playlist.trackCount > 0 ->
                                "${playlist.videoCount} videos, ${playlist.trackCount} songs"
                            playlist.videoCount > 0 -> "${playlist.videoCount} videos"
                            playlist.trackCount > 0 -> "${playlist.trackCount} songs"
                            else -> "Empty"
                        }
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text(itemLabel) },
                            leadingContent = {
                                Icon(Icons.Default.QueueMusic, contentDescription = null)
                            },
                            modifier = Modifier.clickable { onPlaylistSelected(playlist) }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SleepTimerPickerDialog(
    isTimerActive: Boolean,
    remainingTime: Long,
    onTimerSelected: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val timerOptions = listOf(
        15 to "15 minutes",
        30 to "30 minutes",
        45 to "45 minutes",
        60 to "1 hour",
        90 to "1.5 hours",
        120 to "2 hours"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                if (isTimerActive) {
                    // Show active timer info
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Timer Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = formatTimerTime(remainingTime),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            FilledTonalButton(onClick = onCancelTimer) {
                                Text("Cancel")
                            }
                        }
                    }
                    Text(
                        text = "Set new timer:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Timer options as large tap-friendly buttons
                LazyColumn {
                    items(timerOptions.size) { index ->
                        val (minutes, label) = timerOptions[index]
                        Surface(
                            onClick = { onTimerSelected(minutes) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index < timerOptions.size - 1) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
