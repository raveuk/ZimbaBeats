package com.zimbabeats.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.ui.components.EmptyState
import com.zimbabeats.ui.components.PlaylistItemShimmer
import com.zimbabeats.ui.components.VideoListItemShimmer
import com.zimbabeats.ui.viewmodel.LibraryTab
import com.zimbabeats.ui.viewmodel.LibraryViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    viewModel: LibraryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Library",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row with scrollable tabs
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                edgePadding = 16.dp,
                divider = { HorizontalDivider() }
            ) {
                LibraryTabItem(
                    selected = uiState.selectedTab == LibraryTab.FAVORITES,
                    onClick = { viewModel.selectTab(LibraryTab.FAVORITES) },
                    icon = Icons.Default.Favorite,
                    text = "Favorites",
                    count = uiState.favorites.size
                )
                LibraryTabItem(
                    selected = uiState.selectedTab == LibraryTab.PLAYLISTS,
                    onClick = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    text = "Playlists",
                    count = uiState.playlists.size
                )
                LibraryTabItem(
                    selected = uiState.selectedTab == LibraryTab.DOWNLOADS,
                    onClick = { viewModel.selectTab(LibraryTab.DOWNLOADS) },
                    icon = Icons.Default.Download,
                    text = "Downloads",
                    count = uiState.downloads.size
                )
                LibraryTabItem(
                    selected = uiState.selectedTab == LibraryTab.HISTORY,
                    onClick = { viewModel.selectTab(LibraryTab.HISTORY) },
                    icon = Icons.Default.History,
                    text = "History",
                    count = uiState.watchHistory.size
                )
            }

            // Tab Content with crossfade animation
            Crossfade(
                targetState = uiState.selectedTab,
                label = "library_tab_content"
            ) { selectedTab ->
                when (selectedTab) {
                    LibraryTab.FAVORITES -> FavoritesContent(
                        favorites = uiState.favorites,
                        isLoading = uiState.isLoading,
                        onVideoClick = onVideoClick
                    )
                    LibraryTab.PLAYLISTS -> PlaylistsContent(
                        playlists = uiState.playlists,
                        isLoading = uiState.isLoading,
                        onPlaylistClick = onPlaylistClick,
                        onToggleFavorite = { playlistId, isFavorite ->
                            viewModel.togglePlaylistFavorite(playlistId, isFavorite)
                        }
                    )
                    LibraryTab.DOWNLOADS -> DownloadsContent(
                        downloads = uiState.downloads,
                        isLoading = uiState.isLoading,
                        onVideoClick = onVideoClick
                    )
                    LibraryTab.HISTORY -> HistoryContent(
                        history = uiState.watchHistory,
                        isLoading = uiState.isLoading,
                        onVideoClick = onVideoClick,
                        onRemoveFromHistory = { viewModel.removeFromHistory(it) },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    count: Int
) {
    Tab(
        selected = selected,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(20.dp)
            )
            Text(text)
            if (count > 0) {
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesContent(
    favorites: List<Video>,
    isLoading: Boolean,
    onVideoClick: (String) -> Unit
) {
    Crossfade(targetState = isLoading, label = "favorites_loading") { loading ->
        when {
            loading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(6) { VideoListItemShimmer() }
                }
            }
            favorites.isEmpty() -> {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    title = "No favorites yet",
                    subtitle = "Videos you love will appear here",
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favorites, key = { it.id }) { video ->
                        EnhancedVideoItem(
                            video = video,
                            onClick = { onVideoClick(video.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsContent(
    playlists: List<Playlist>,
    isLoading: Boolean,
    onPlaylistClick: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit
) {
    Crossfade(targetState = isLoading, label = "playlists_loading") { loading ->
        when {
            loading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(6) { PlaylistItemShimmer() }
                }
            }
            playlists.isEmpty() -> {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    title = "No playlists yet",
                    subtitle = "Create playlists to organize your videos",
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        EnhancedPlaylistItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) },
                            onToggleFavorite = { onToggleFavorite(playlist.id, playlist.isFavorite) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsContent(
    downloads: List<Video>,
    isLoading: Boolean,
    onVideoClick: (String) -> Unit
) {
    Crossfade(targetState = isLoading, label = "downloads_loading") { loading ->
        when {
            loading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(6) { VideoListItemShimmer() }
                }
            }
            downloads.isEmpty() -> {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.Default.DownloadDone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    title = "No downloads yet",
                    subtitle = "Downloaded videos will appear here",
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(downloads, key = { it.id }) { video ->
                        EnhancedVideoItem(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            showDownloadIcon = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    history: List<Video>,
    isLoading: Boolean,
    onVideoClick: (String) -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Crossfade(targetState = isLoading, label = "history_loading") { loading ->
        when {
            loading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(6) { VideoListItemShimmer() }
                }
            }
            history.isEmpty() -> {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    title = "No watch history",
                    subtitle = "Videos you watch will appear here",
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Clear history button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteSweep, "Clear", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear All")
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Use index in key to handle duplicate videos in history
                        itemsIndexed(history, key = { index, video -> "${video.id}_$index" }) { _, video ->
                            EnhancedVideoItem(
                                video = video,
                                onClick = { onVideoClick(video.id) },
                                onRemove = { onRemoveFromHistory(video.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Clear history confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear Watch History") },
            text = { Text("Are you sure you want to clear your entire watch history? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
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
}

@Composable
private fun EnhancedVideoItem(
    video: Video,
    onClick: () -> Unit,
    showDownloadIcon: Boolean = false,
    onRemove: (() -> Unit)? = null
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
            // Thumbnail with play overlay
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
                        .size(28.dp),
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

                // Download icon
                if (showDownloadIcon) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            Icons.Default.DownloadDone,
                            "Downloaded",
                            modifier = Modifier
                                .size(18.dp)
                                .padding(2.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

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

                if (video.viewCount > 0) {
                    Text(
                        text = formatViewCount(video.viewCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        "Remove"
                    )
                }
            }
        }
    }
}

@Composable
private fun EnhancedPlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val color = try {
        Color(android.graphics.Color.parseColor(playlist.color.hex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator with icon
            Surface(
                modifier = Modifier.size(56.dp),
                color = color,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                playlist.description?.let { description ->
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playlist.trackCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (playlist.videoCount > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${playlist.videoCount} ${if (playlist.videoCount == 1) "video" else "videos"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (playlist.trackCount == 0 && playlist.videoCount == 0) {
                        Text(
                            text = "Empty",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (playlist.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (playlist.isFavorite) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
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

private fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM views", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK views", count / 1_000.0)
        else -> "$count views"
    }
}
