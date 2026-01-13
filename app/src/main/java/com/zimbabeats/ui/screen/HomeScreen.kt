package com.zimbabeats.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Album
import com.zimbabeats.core.domain.model.music.Artist
import com.zimbabeats.core.domain.model.music.MusicBrowseItem
import com.zimbabeats.core.domain.model.music.MusicBrowseSection
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.model.music.YouTubeMusicPlaylist
import com.zimbabeats.ui.accessibility.ContentDescriptions
import com.zimbabeats.ui.components.EnhancedVideoCard
import com.zimbabeats.ui.components.HomeShimmer
import com.zimbabeats.ui.components.SectionHeader
import com.zimbabeats.ui.viewmodel.HomeViewModel
import com.zimbabeats.ui.viewmodel.HomeUiState
import com.zimbabeats.ui.viewmodel.PopularChannel
import com.zimbabeats.ui.viewmodel.music.MusicHomeViewModel
import com.zimbabeats.ui.util.WindowSizeUtil
import org.koin.androidx.compose.koinViewModel

enum class ContentMode {
    VIDEO, MUSIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: (ContentMode) -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onVideoClick: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onYouTubeMusicPlaylistClick: (String) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    musicViewModel: MusicHomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val musicUiState by musicViewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    var selectedContentMode by rememberSaveable { mutableStateOf(ContentMode.VIDEO) }

    // Use ViewModel's refreshing state
    val isRefreshing = if (selectedContentMode == ContentMode.VIDEO) uiState.isRefreshing else musicUiState.isRefreshing
    val isLoading = if (selectedContentMode == ContentMode.VIDEO) uiState.isLoading else musicUiState.isLoading

    Scaffold(
        topBar = {
            // Spotify-style top bar with gradient
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Zimba Beats Player",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            ContentDescriptions.OPEN_SETTINGS,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Search Box
                Surface(
                    onClick = { onNavigateToSearch(selectedContentMode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (selectedContentMode == ContentMode.VIDEO) Icons.Default.Search
                            else Icons.Default.MusicNote,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (selectedContentMode == ContentMode.VIDEO)
                                "What do you want to watch?"
                            else
                                "What do you want to listen to?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Video/Music Toggle Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedContentMode == ContentMode.VIDEO,
                        onClick = { selectedContentMode = ContentMode.VIDEO },
                        label = { Text("Videos") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = selectedContentMode == ContentMode.MUSIC,
                        onClick = {
                            selectedContentMode = ContentMode.MUSIC
                        },
                        label = { Text("Music") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (selectedContentMode == ContentMode.VIDEO) {
                    viewModel.refreshVideos()
                } else {
                    musicViewModel.refresh()
                }
            },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Use key to force recomposition when parental settings change
            val videoContentKey = "${uiState.parentalControlEnabled}_${uiState.selectedAgeLevel.name}_${uiState.videos.size}"
            val musicContentKey = "${musicUiState.parentalControlEnabled}_${musicUiState.sections.size}"

            Crossfade(
                targetState = Triple(selectedContentMode, isLoading && !isRefreshing, if (selectedContentMode == ContentMode.VIDEO) videoContentKey else musicContentKey),
                label = "home_content"
            ) { (mode, loading, _) ->
                if (loading) {
                    HomeShimmer()
                } else {
                    when (mode) {
                        ContentMode.VIDEO -> {
                            VideoHomeContent(
                                uiState = uiState,
                                isKidMode = uiState.parentalControlEnabled,
                                onVideoClick = onVideoClick,
                                onNavigateToPlaylists = onNavigateToPlaylists,
                                onNavigateToLibrary = onNavigateToLibrary,
                                onChannelClick = { channel -> viewModel.loadChannelVideos(channel) }
                            )
                        }
                        ContentMode.MUSIC -> {
                            MusicHomeContent(
                                uiState = musicUiState,
                                isKidMode = uiState.parentalControlEnabled,
                                onTrackClick = onTrackClick,
                                onAlbumClick = onAlbumClick,
                                onArtistClick = onArtistClick,
                                onPlaylistClick = onYouTubeMusicPlaylistClick,
                                onNavigateToPlaylists = onNavigateToPlaylists,
                                onNavigateToLibrary = onNavigateToLibrary
                            )
                        }
                    }
                }
            }
        }

        // Error snackbar
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun VideoHomeContent(
    uiState: HomeUiState,
    isKidMode: Boolean,
    onVideoClick: (String) -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onChannelClick: (PopularChannel) -> Unit
) {
    val hasContent = uiState.videos.isNotEmpty() ||
            uiState.recentlyWatched.isNotEmpty() ||
            uiState.mostWatched.isNotEmpty() ||
            uiState.favorites.isNotEmpty() ||
            uiState.quickPicks.isNotEmpty()

    if (!hasContent && uiState.isLoading) {
        // Show loading shimmer
        HomeShimmer()
    } else if (!hasContent) {
        // Empty state - adaptive based on mode
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (isKidMode)
                        "No videos yet. Use the search bar above to find fun and educational videos."
                    else
                        "No videos yet. Use the search bar above to start exploring."
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val iconSize = WindowSizeUtil.getLargeIconSize()
            Icon(
                Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                if (isKidMode) "Let's Watch!" else "Start Exploring",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isKidMode)
                    "Search for cartoons, learning videos, and more!"
                else
                    "Search for videos to start building your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Quick Action Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        QuickActionChip(
                            text = "Playlists",
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            onClick = onNavigateToPlaylists
                        )
                    }
                    item {
                        QuickActionChip(
                            text = "Library",
                            icon = Icons.Default.VideoLibrary,
                            onClick = onNavigateToLibrary
                        )
                    }
                }
            }

            // Quick Picks Grid (4 items in 2x2 or horizontal scroll)
            if (uiState.quickPicks.isNotEmpty()) {
                item {
                    QuickPicksSection(
                        title = if (isKidMode) "Quick Picks" else "For You",
                        subtitle = if (isKidMode) "Fun videos just for you" else "Recommended videos",
                        videos = uiState.quickPicks.take(8),
                        onVideoClick = onVideoClick
                    )
                }
            }

            // Continue Watching Section
            if (uiState.recentlyWatched.isNotEmpty()) {
                item {
                    VideoSection(
                        title = if (isKidMode) "Keep Watching" else "Continue Watching",
                        subtitle = if (isKidMode) "Your favorite shows" else "Pick up where you left off",
                        videos = uiState.recentlyWatched,
                        onVideoClick = onVideoClick
                    )
                }
            }

            // Trending/For You Section (moved above Popular Channels)
            if (uiState.videos.isNotEmpty()) {
                item {
                    VideoSection(
                        title = if (isKidMode) "Fun Videos" else "Trending",
                        subtitle = if (isKidMode) "Safe and fun content for you" else "Popular right now",
                        videos = uiState.videos,
                        onVideoClick = onVideoClick
                    )
                }
            }

            // Most Watched Section
            if (uiState.mostWatched.isNotEmpty()) {
                item {
                    VideoSection(
                        title = if (isKidMode) "Watch Again" else "Most Watched",
                        subtitle = if (isKidMode) "Videos you love" else "Your top videos",
                        videos = uiState.mostWatched,
                        onVideoClick = onVideoClick
                    )
                }
            }

            // Mood-Based Sections
            uiState.moodSections.filter { it.videos.isNotEmpty() }.forEach { moodSection ->
                item {
                    VideoSection(
                        title = moodSection.title,
                        subtitle = moodSection.subtitle,
                        videos = moodSection.videos,
                        onVideoClick = onVideoClick
                    )
                }
            }

            // Favorites Section
            if (uiState.favorites.isNotEmpty()) {
                item {
                    VideoSection(
                        title = if (isKidMode) "My Favorites" else "Your Favorites",
                        subtitle = if (isKidMode) "Videos you love" else "Videos you've liked",
                        videos = uiState.favorites,
                        onVideoClick = onVideoClick
                    )
                }
            }

            // Bottom spacing for mini player
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

// ==================== NEW SECTION COMPONENTS ====================

/**
 * Quick Picks section with grid-like layout
 */
@Composable
private fun QuickPicksSection(
    title: String,
    subtitle: String?,
    videos: List<Video>,
    onVideoClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // 2-row horizontal grid
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            // Pair videos for 2-row layout
            val pairedVideos = videos.chunked(2)
            items(pairedVideos) { pair ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { video ->
                        QuickPickItem(
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
private fun QuickPickItem(
    video: Video,
    onClick: () -> Unit
) {
    val quickPickWidth = WindowSizeUtil.getQuickPickWidth()
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(quickPickWidth)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Popular Channels section
 */
@Composable
private fun ChannelsSection(
    title: String,
    channels: List<PopularChannel>,
    channelVideos: Map<String, List<Video>>,
    onChannelClick: (PopularChannel) -> Unit,
    onVideoClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(
                items = channels,
                key = { it.id }  // Stable key
            ) { channel ->
                ChannelCard(
                    channel = channel,
                    videos = channelVideos[channel.id] ?: emptyList(),
                    onClick = { onChannelClick(channel) },
                    onVideoClick = onVideoClick
                )
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: PopularChannel,
    videos: List<Video>,
    onClick: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(160.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Channel icon/thumbnail
            if (videos.isNotEmpty()) {
                AsyncImage(
                    model = videos.first().thumbnailUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name.first().toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Show first video if available
            if (videos.isNotEmpty()) {
                Text(
                    text = "${videos.size} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Tap to load",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Spotify-style quick action chip
 */
@Composable
private fun QuickActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.semantics {
            contentDescription = "Go to $text"
            role = Role.Button
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null, // Part of parent semantics
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun VideoSection(
    title: String,
    subtitle: String? = null,
    videos: List<Video>,
    onVideoClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(
                items = videos,
                key = { it.id }  // Stable key for better recomposition
            ) { video ->
                EnhancedVideoCard(
                    video = video,
                    onClick = { onVideoClick(video.id) }
                )
            }
        }
    }
}

// ==================== MUSIC HOME CONTENT ====================

@Composable
private fun MusicHomeContent(
    uiState: com.zimbabeats.ui.viewmodel.music.MusicHomeUiState,
    isKidMode: Boolean,
    onTrackClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    val hasContent = uiState.sections.isNotEmpty() ||
            uiState.recentlyPlayed.isNotEmpty() ||
            uiState.mostPlayed.isNotEmpty()

    if (!hasContent) {
        // Empty state for music - adaptive based on mode
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (isKidMode)
                        "No music yet. Search for fun songs, lullabies, and sing-alongs!"
                    else
                        "No music yet. Use the search bar above to discover music."
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val iconSize = WindowSizeUtil.getLargeIconSize()
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                if (isKidMode) "Let's Listen!" else "Discover Music",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isKidMode)
                    "Search for lullabies, sing-alongs, and fun songs!"
                else
                    "Search for your favorite songs and artists",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Quick Action Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        QuickActionChip(
                            text = "Playlists",
                            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                            onClick = onNavigateToPlaylists
                        )
                    }
                    item {
                        QuickActionChip(
                            text = "Library",
                            icon = Icons.Default.MusicNote,
                            onClick = onNavigateToLibrary
                        )
                    }
                }
            }

            // Recently Played Section
            if (uiState.recentlyPlayed.isNotEmpty()) {
                item {
                    TrackSection(
                        title = if (isKidMode) "Play Again" else "Recently Played",
                        subtitle = if (isKidMode) "Songs you like" else "Pick up where you left off",
                        tracks = uiState.recentlyPlayed,
                        onTrackClick = onTrackClick
                    )
                }
            }

            // Most Played Section
            if (uiState.mostPlayed.isNotEmpty()) {
                item {
                    TrackSection(
                        title = if (isKidMode) "Your Favorites" else "Your Top Tracks",
                        subtitle = if (isKidMode) "Songs you play the most" else "Your most played songs",
                        tracks = uiState.mostPlayed,
                        onTrackClick = onTrackClick
                    )
                }
            }

            // Browse Sections from YouTube Music
            items(uiState.sections) { section ->
                MusicBrowseSectionUI(
                    section = section,
                    onTrackClick = onTrackClick,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick
                )
            }

            // Bottom spacing for mini player
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun TrackSection(
    title: String,
    subtitle: String? = null,
    tracks: List<Track>,
    onTrackClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(
                items = tracks,
                key = { it.id }  // Stable key for better recomposition
            ) { track ->
                TrackCard(
                    track = track,
                    onClick = { onTrackClick(track.id) }
                )
            }
        }
    }
}

@Composable
private fun MusicBrowseSectionUI(
    section: MusicBrowseSection,
    onTrackClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = section.title,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(
                items = section.items,
                key = { item ->
                    when (item) {
                        is MusicBrowseItem.TrackItem -> "track_${item.track.id}"
                        is MusicBrowseItem.AlbumItem -> "album_${item.album.id}"
                        is MusicBrowseItem.ArtistItem -> "artist_${item.artist.id}"
                        is MusicBrowseItem.PlaylistItem -> "playlist_${item.playlist.id}"
                    }
                }
            ) { item ->
                when (item) {
                    is MusicBrowseItem.TrackItem -> {
                        TrackCard(
                            track = item.track,
                            onClick = { onTrackClick(item.track.id) }
                        )
                    }
                    is MusicBrowseItem.AlbumItem -> {
                        AlbumCard(
                            album = item.album,
                            onClick = { onAlbumClick(item.album.id) }
                        )
                    }
                    is MusicBrowseItem.ArtistItem -> {
                        ArtistCard(
                            artist = item.artist,
                            onClick = { onArtistClick(item.artist.id) }
                        )
                    }
                    is MusicBrowseItem.PlaylistItem -> {
                        PlaylistCard(
                            playlist = item.playlist,
                            onClick = { onPlaylistClick(item.playlist.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = WindowSizeUtil.getCardWidth()
    Surface(
        onClick = onClick,
        modifier = modifier.width(cardWidth),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
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
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = WindowSizeUtil.getCardWidth()
    Surface(
        onClick = onClick,
        modifier = modifier.width(cardWidth),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            AsyncImage(
                model = album.thumbnailUrl,
                contentDescription = album.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(120.dp),
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = artist.thumbnailUrl,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: YouTubeMusicPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardWidth = WindowSizeUtil.getCardWidth()
    Surface(
        onClick = onClick,
        modifier = modifier.width(cardWidth),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = playlist.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (playlist.trackCount > 0) {
                        "${playlist.trackCount} tracks"
                    } else {
                        playlist.author ?: "Playlist"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
