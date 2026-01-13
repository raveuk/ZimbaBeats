package com.zimbabeats.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.media.music.MusicPlaybackManager
import com.zimbabeats.ui.theme.ZimbaBeatsCorners
import com.zimbabeats.ui.viewmodel.LibraryViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// Detail screen types
private enum class LibraryDetailScreen {
    NONE, LIKED_SONGS, LIKED_VIDEOS, MOST_PLAYED, PLAYLISTS, HISTORY, DOWNLOADS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onNavigateToImport: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onMusicTrackClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = koinViewModel(),
    musicPlaybackManager: MusicPlaybackManager = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var currentDetailScreen by remember { mutableStateOf(LibraryDetailScreen.NONE) }

    // Handle back press
    val handleBack: () -> Unit = {
        if (currentDetailScreen != LibraryDetailScreen.NONE) {
            currentDetailScreen = LibraryDetailScreen.NONE
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentDetailScreen) {
                            LibraryDetailScreen.NONE -> "Your Library"
                            LibraryDetailScreen.LIKED_SONGS -> "Liked Songs"
                            LibraryDetailScreen.LIKED_VIDEOS -> "Liked Videos"
                            LibraryDetailScreen.MOST_PLAYED -> "Most Played"
                            LibraryDetailScreen.PLAYLISTS -> "Playlists"
                            LibraryDetailScreen.HISTORY -> "Watch History"
                            LibraryDetailScreen.DOWNLOADS -> "Downloads"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = if (currentDetailScreen == LibraryDetailScreen.NONE) 28.sp else 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (currentDetailScreen == LibraryDetailScreen.NONE) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Create Playlist") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToPlaylists()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Playlist") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToImport()
                                }
                            )
                        }
                    } else if (currentDetailScreen == LibraryDetailScreen.HISTORY) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentDetailScreen,
            transitionSpec = {
                if (targetState == LibraryDetailScreen.NONE) {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                } else {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                }
            },
            label = "library_content"
        ) { screen ->
            when (screen) {
                LibraryDetailScreen.NONE -> {
                    LibraryMainContent(
                        uiState = uiState,
                        paddingValues = paddingValues,
                        musicPlaybackManager = musicPlaybackManager,
                        onMusicTrackClick = onMusicTrackClick,
                        onVideoClick = onVideoClick,
                        onPlaylistClick = onPlaylistClick,
                        onTogglePlaylistFavorite = { id, isFav -> viewModel.togglePlaylistFavorite(id, isFav) },
                        onNavigateToLikedSongs = { currentDetailScreen = LibraryDetailScreen.LIKED_SONGS },
                        onNavigateToLikedVideos = { currentDetailScreen = LibraryDetailScreen.LIKED_VIDEOS },
                        onNavigateToMostPlayed = { currentDetailScreen = LibraryDetailScreen.MOST_PLAYED },
                        onNavigateToPlaylists = { currentDetailScreen = LibraryDetailScreen.PLAYLISTS },
                        onNavigateToHistory = { currentDetailScreen = LibraryDetailScreen.HISTORY },
                        onNavigateToDownloads = { currentDetailScreen = LibraryDetailScreen.DOWNLOADS }
                    )
                }
                LibraryDetailScreen.LIKED_SONGS -> {
                    LikedSongsDetailScreen(
                        musicFavorites = uiState.musicFavorites,
                        paddingValues = paddingValues,
                        musicPlaybackManager = musicPlaybackManager,
                        onMusicTrackClick = onMusicTrackClick
                    )
                }
                LibraryDetailScreen.LIKED_VIDEOS -> {
                    LikedVideosDetailScreen(
                        videoFavorites = uiState.favorites,
                        paddingValues = paddingValues,
                        onVideoClick = onVideoClick
                    )
                }
                LibraryDetailScreen.MOST_PLAYED -> {
                    MostPlayedDetailScreen(
                        tracks = uiState.mostPlayedTracks,
                        paddingValues = paddingValues,
                        musicPlaybackManager = musicPlaybackManager,
                        onMusicTrackClick = onMusicTrackClick
                    )
                }
                LibraryDetailScreen.PLAYLISTS -> {
                    PlaylistsDetailScreen(
                        playlists = uiState.playlists,
                        paddingValues = paddingValues,
                        onPlaylistClick = onPlaylistClick,
                        onToggleFavorite = { id, isFav -> viewModel.togglePlaylistFavorite(id, isFav) }
                    )
                }
                LibraryDetailScreen.HISTORY -> {
                    HistoryDetailScreen(
                        history = uiState.watchHistory,
                        paddingValues = paddingValues,
                        onVideoClick = onVideoClick,
                        onRemoveFromHistory = { viewModel.removeFromHistory(it) }
                    )
                }
                LibraryDetailScreen.DOWNLOADS -> {
                    DownloadsDetailScreen(
                        downloads = uiState.downloads,
                        paddingValues = paddingValues,
                        onVideoClick = onVideoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryMainContent(
    uiState: com.zimbabeats.ui.viewmodel.LibraryUiState,
    paddingValues: PaddingValues,
    musicPlaybackManager: MusicPlaybackManager,
    onMusicTrackClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onTogglePlaylistFavorite: (Long, Boolean) -> Unit,
    onNavigateToLikedSongs: () -> Unit,
    onNavigateToLikedVideos: () -> Unit,
    onNavigateToMostPlayed: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Hero Section
        item {
            if (uiState.musicFavorites.isNotEmpty() || uiState.mostPlayedTracks.isNotEmpty()) {
                HeroSection(
                    track = uiState.mostPlayedTracks.firstOrNull()
                        ?: uiState.musicFavorites.firstOrNull(),
                    onPlayClick = { track ->
                        track?.let {
                            val tracks = uiState.mostPlayedTracks.ifEmpty { uiState.musicFavorites }
                            musicPlaybackManager.playTracks(tracks, 0)
                            onMusicTrackClick(it.id)
                        }
                    }
                )
            }
        }

        // Quick Access Pills
        item {
            QuickAccessRow(
                favoritesCount = uiState.favorites.size + uiState.musicFavorites.size,
                playlistsCount = uiState.playlists.size,
                downloadsCount = uiState.downloads.size,
                onLikedClick = onNavigateToLikedSongs,
                onPlaylistsClick = onNavigateToPlaylists,
                onDownloadsClick = onNavigateToDownloads
            )
        }

        // Favorite Tracks Section
        if (uiState.musicFavorites.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Liked Songs",
                    subtitle = "${uiState.musicFavorites.size} songs",
                    icon = Icons.Default.Favorite,
                    iconTint = Color(0xFFEF4444),
                    onSeeAllClick = onNavigateToLikedSongs
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    itemsIndexed(
                        items = uiState.musicFavorites.take(10),
                        key = { _, track -> track.id }
                    ) { index, track ->
                        AlbumArtCard(
                            track = track,
                            onClick = {
                                musicPlaybackManager.playTracks(uiState.musicFavorites, index)
                                onMusicTrackClick(track.id)
                            }
                        )
                    }
                    if (uiState.musicFavorites.size > 10) {
                        item {
                            SeeAllCard(
                                totalCount = uiState.musicFavorites.size,
                                itemsShown = 10,
                                onClick = onNavigateToLikedSongs
                            )
                        }
                    }
                }
            }
        }

        // Liked Videos Section
        if (uiState.favorites.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Liked Videos",
                    subtitle = "${uiState.favorites.size} videos",
                    icon = Icons.Default.VideoLibrary,
                    iconTint = Color(0xFF06B6D4),
                    onSeeAllClick = onNavigateToLikedVideos
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(
                        items = uiState.favorites.take(8),
                        key = { it.id }
                    ) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video.id) }
                        )
                    }
                    if (uiState.favorites.size > 8) {
                        item {
                            SeeAllCard(
                                totalCount = uiState.favorites.size,
                                itemsShown = 8,
                                onClick = onNavigateToLikedVideos
                            )
                        }
                    }
                }
            }
        }

        // Most Played Section
        if (uiState.mostPlayedTracks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Most Played",
                    subtitle = "Your top tracks",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconTint = Color(0xFF10B981),
                    onSeeAllClick = onNavigateToMostPlayed
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    itemsIndexed(
                        items = uiState.mostPlayedTracks.take(10),
                        key = { _, track -> track.id }
                    ) { index, track ->
                        RankedTrackCard(
                            track = track,
                            rank = index + 1,
                            onClick = {
                                musicPlaybackManager.playTracks(uiState.mostPlayedTracks, index)
                                onMusicTrackClick(track.id)
                            }
                        )
                    }
                }
            }
        }

        // Playlists Section
        if (uiState.playlists.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Your Playlists",
                    subtitle = "${uiState.playlists.size} playlists",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    iconTint = Color(0xFF8B5CF6),
                    onSeeAllClick = onNavigateToPlaylists
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(
                        items = uiState.playlists.take(6),
                        key = { it.id }
                    ) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) },
                            onFavoriteClick = { onTogglePlaylistFavorite(playlist.id, playlist.isFavorite) }
                        )
                    }
                    if (uiState.playlists.size > 6) {
                        item {
                            SeeAllCard(
                                totalCount = uiState.playlists.size,
                                itemsShown = 6,
                                onClick = onNavigateToPlaylists
                            )
                        }
                    }
                }
            }
        }

        // Recently Watched Videos
        if (uiState.watchHistory.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently Watched",
                    subtitle = "Continue watching",
                    icon = Icons.Default.History,
                    iconTint = Color(0xFF06B6D4),
                    onSeeAllClick = onNavigateToHistory
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(
                        items = uiState.watchHistory.take(8),
                        key = { it.id }
                    ) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video.id) }
                        )
                    }
                    if (uiState.watchHistory.size > 8) {
                        item {
                            SeeAllCard(
                                totalCount = uiState.watchHistory.size,
                                itemsShown = 8,
                                onClick = onNavigateToHistory
                            )
                        }
                    }
                }
            }
        }

        // Downloads Section
        if (uiState.downloads.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Downloads",
                    subtitle = "${uiState.downloads.size} videos offline",
                    icon = Icons.Default.Download,
                    iconTint = Color(0xFFF59E0B),
                    onSeeAllClick = onNavigateToDownloads
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(
                        items = uiState.downloads.take(6),
                        key = { it.id }
                    ) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            showDownloadBadge = true
                        )
                    }
                    if (uiState.downloads.size > 6) {
                        item {
                            SeeAllCard(
                                totalCount = uiState.downloads.size,
                                itemsShown = 6,
                                onClick = onNavigateToDownloads
                            )
                        }
                    }
                }
            }
        }

        // Empty State
        if (uiState.musicFavorites.isEmpty() &&
            uiState.playlists.isEmpty() &&
            uiState.downloads.isEmpty() &&
            uiState.watchHistory.isEmpty()) {
            item {
                EmptyLibraryState()
            }
        }
    }
}

// ==================== Detail Screens ====================

@Composable
private fun LikedSongsDetailScreen(
    musicFavorites: List<Track>,
    paddingValues: PaddingValues,
    musicPlaybackManager: MusicPlaybackManager,
    onMusicTrackClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play All button
        if (musicFavorites.isNotEmpty()) {
            item {
                PlayAllButton(
                    count = musicFavorites.size,
                    onClick = {
                        musicPlaybackManager.playTracks(musicFavorites, 0)
                        onMusicTrackClick(musicFavorites.first().id)
                    }
                )
            }

            itemsIndexed(musicFavorites, key = { _, track -> track.id }) { index, track ->
                TrackListItem(
                    track = track,
                    index = index + 1,
                    onClick = {
                        musicPlaybackManager.playTracks(musicFavorites, index)
                        onMusicTrackClick(track.id)
                    }
                )
            }
        }

        // Empty State
        if (musicFavorites.isEmpty()) {
            item {
                EmptyDetailState(
                    icon = Icons.Default.FavoriteBorder,
                    title = "No liked songs yet",
                    subtitle = "Tap the heart on songs you love"
                )
            }
        }
    }
}

@Composable
private fun LikedVideosDetailScreen(
    videoFavorites: List<Video>,
    paddingValues: PaddingValues,
    onVideoClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (videoFavorites.isNotEmpty()) {
            items(videoFavorites, key = { it.id }) { video ->
                VideoListItem(
                    video = video,
                    onClick = { onVideoClick(video.id) }
                )
            }
        }

        // Empty State
        if (videoFavorites.isEmpty()) {
            item {
                EmptyDetailState(
                    icon = Icons.Default.VideoLibrary,
                    title = "No liked videos yet",
                    subtitle = "Tap the heart on videos you love"
                )
            }
        }
    }
}

@Composable
private fun MostPlayedDetailScreen(
    tracks: List<Track>,
    paddingValues: PaddingValues,
    musicPlaybackManager: MusicPlaybackManager,
    onMusicTrackClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (tracks.isNotEmpty()) {
            item {
                PlayAllButton(
                    count = tracks.size,
                    onClick = {
                        musicPlaybackManager.playTracks(tracks, 0)
                        onMusicTrackClick(tracks.first().id)
                    }
                )
            }
        }

        itemsIndexed(tracks) { index, track ->
            RankedTrackListItem(
                track = track,
                rank = index + 1,
                onClick = {
                    musicPlaybackManager.playTracks(tracks, index)
                    onMusicTrackClick(track.id)
                }
            )
        }

        if (tracks.isEmpty()) {
            item {
                EmptyDetailState(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    title = "No plays yet",
                    subtitle = "Start listening to see your top tracks"
                )
            }
        }
    }
}

@Composable
private fun PlaylistsDetailScreen(
    playlists: List<Playlist>,
    paddingValues: PaddingValues,
    onPlaylistClick: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists) { playlist ->
            PlaylistListItem(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist.id) },
                onToggleFavorite = { onToggleFavorite(playlist.id, playlist.isFavorite) }
            )
        }

        if (playlists.isEmpty()) {
            item {
                EmptyDetailState(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = "No playlists yet",
                    subtitle = "Create playlists to organize your music"
                )
            }
        }
    }
}

@Composable
private fun HistoryDetailScreen(
    history: List<Video>,
    paddingValues: PaddingValues,
    onVideoClick: (String) -> Unit,
    onRemoveFromHistory: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(history, key = { _, video -> video.id }) { _, video ->
            VideoListItem(
                video = video,
                onClick = { onVideoClick(video.id) },
                onRemove = { onRemoveFromHistory(video.id) }
            )
        }

        if (history.isEmpty()) {
            item {
                EmptyDetailState(
                    icon = Icons.Default.History,
                    title = "No watch history",
                    subtitle = "Videos you watch will appear here"
                )
            }
        }
    }
}

@Composable
private fun DownloadsDetailScreen(
    downloads: List<Video>,
    paddingValues: PaddingValues,
    onVideoClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(downloads) { video ->
            VideoListItem(
                video = video,
                onClick = { onVideoClick(video.id) },
                showDownloadBadge = true
            )
        }

        if (downloads.isEmpty()) {
            item {
                EmptyDetailState(
                    icon = Icons.Default.DownloadDone,
                    title = "No downloads",
                    subtitle = "Downloaded videos will appear here"
                )
            }
        }
    }
}

// ==================== Detail Components ====================

@Composable
private fun PlayAllButton(
    count: Int,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1DB954)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Play All ($count)",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailSectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Surface(
            color = color.copy(alpha = 0.2f),
            shape = ZimbaBeatsCorners.Thumbnail
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TrackListItem(
    track: Track,
    index: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Index
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )

            // Album art
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(ZimbaBeatsCorners.Thumbnail),
                contentScale = ContentScale.Crop
            )

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            if (track.duration > 0) {
                Text(
                    text = formatDuration(track.duration / 1000),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun RankedTrackListItem(
    track: Track,
    rank: Int,
    onClick: () -> Unit
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color.White.copy(alpha = 0.5f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank badge
            Surface(
                color = if (rank <= 3) rankColor.copy(alpha = 0.2f) else Color.Transparent,
                shape = ZimbaBeatsCorners.Thumbnail,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = rankColor
                    )
                }
            }

            // Album art
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(ZimbaBeatsCorners.Thumbnail),
                contentScale = ContentScale.Crop
            )

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlaylistListItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val color = try {
        Color(android.graphics.Color.parseColor(playlist.color.hex))
    } catch (e: Exception) {
        Color(0xFF8B5CF6)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color indicator
            Surface(
                modifier = Modifier.size(56.dp),
                color = color,
                shape = ZimbaBeatsCorners.Thumbnail
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Playlist info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount + playlist.videoCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Favorite button
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (playlist.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (playlist.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (playlist.isFavorite) Color(0xFFEF4444) else Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun VideoListItem(
    video: Video,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    showDownloadBadge: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(100.dp, 56.dp)
                    .clip(ZimbaBeatsCorners.Thumbnail)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Duration badge
                if (video.duration > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = ZimbaBeatsCorners.ExtraSmall,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = formatDuration(video.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (showDownloadBadge) {
                    Surface(
                        color = Color(0xFF1DB954),
                        shape = ZimbaBeatsCorners.ExtraSmall,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(2.dp)
                        )
                    }
                }
            }

            // Video info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Remove button (for history)
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove from history",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDetailState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            modifier = Modifier.size(80.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

// ==================== Main Screen Components ====================

@Composable
private fun HeroSection(
    track: Track?,
    onPlayClick: (Track?) -> Unit
) {
    if (track == null) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 16.dp)
            .clip(ZimbaBeatsCorners.ExtraLarge)
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.2f),
                shape = ZimbaBeatsCorners.Large
            ) {
                Text(
                    text = "CONTINUE LISTENING",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artistName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }

                FloatingActionButton(
                    onClick = { onPlayClick(track) },
                    containerColor = Color(0xFF1DB954),
                    contentColor = Color.Black,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessRow(
    favoritesCount: Int,
    playlistsCount: Int,
    downloadsCount: Int,
    onLikedClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onDownloadsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickAccessPill(
            icon = Icons.Default.Favorite,
            label = "Liked",
            count = favoritesCount,
            color = Color(0xFFEF4444),
            modifier = Modifier.weight(1f),
            onClick = onLikedClick
        )
        QuickAccessPill(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            label = "Playlists",
            count = playlistsCount,
            color = Color(0xFF8B5CF6),
            modifier = Modifier.weight(1f),
            onClick = onPlaylistsClick
        )
        QuickAccessPill(
            icon = Icons.Default.Download,
            label = "Downloads",
            count = downloadsCount,
            color = Color(0xFFF59E0B),
            modifier = Modifier.weight(1f),
            onClick = onDownloadsClick
        )
    }
}

@Composable
private fun QuickAccessPill(
    icon: ImageVector,
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onSeeAllClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = iconTint.copy(alpha = 0.15f),
                shape = ZimbaBeatsCorners.Medium
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        IconButton(onClick = onSeeAllClick) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "See all",
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AlbumArtCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF1DB954),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        Column {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RankedTrackCard(
    track: Track,
    rank: Int,
    onClick: () -> Unit
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color.White.copy(alpha = 0.5f)
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(bottomEnd = 12.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = rankColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.Black,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }

        Column {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val color = try {
        Color(android.graphics.Color.parseColor(playlist.color.hex))
    } catch (e: Exception) {
        Color(0xFF8B5CF6)
    }

    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp, 100.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    Brush.linearGradient(
                        colors = listOf(color, color.copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )

            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = if (playlist.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (playlist.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (playlist.isFavorite) Color(0xFFEF4444) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackCount + playlist.videoCount} items",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    showDownloadBadge: Boolean = false
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (video.duration > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = ZimbaBeatsCorners.ExtraSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (showDownloadBadge) {
                Surface(
                    color = Color(0xFF1DB954),
                    shape = ZimbaBeatsCorners.ExtraSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        Column {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeeAllCard(
    totalCount: Int,
    itemsShown: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(140.dp)
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "+${totalCount - itemsShown}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "See All",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EmptyLibraryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            modifier = Modifier.size(80.dp)
        ) {
            Icon(
                Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            )
        }

        Text(
            text = "Your library is empty",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = "Start exploring music and videos to build your collection",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
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
