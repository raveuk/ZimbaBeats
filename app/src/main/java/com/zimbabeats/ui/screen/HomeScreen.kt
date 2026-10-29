package com.zimbabeats.ui.screen

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.background
                    ),
                    startY = 0f,
                    endY = 1000f
                )
            )
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        Scaffold(
            containerColor = Color.Transparent
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
                                    selectedContentMode = selectedContentMode,
                                    onContentModeSelected = { selectedContentMode = it },
                                    onNavigateToSearch = onNavigateToSearch,
                                    onNavigateToSettings = onNavigateToSettings,
                                    isLandscape = isLandscape,
                                    onVideoClick = onVideoClick,
                                    onChannelClick = { channel -> viewModel.loadChannelVideos(channel) }
                                )
                            }
                            ContentMode.MUSIC -> {
                                MusicHomeContent(
                                    uiState = musicUiState,
                                    isKidMode = uiState.parentalControlEnabled,
                                    selectedContentMode = selectedContentMode,
                                    onContentModeSelected = { selectedContentMode = it },
                                    onNavigateToSearch = onNavigateToSearch,
                                    onNavigateToSettings = onNavigateToSettings,
                                    isLandscape = isLandscape,
                                    onTrackClick = onTrackClick,
                                    onAlbumClick = onAlbumClick,
                                    onArtistClick = onArtistClick,
                                    onPlaylistClick = onYouTubeMusicPlaylistClick
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
}

@Composable
private fun VideoHomeContent(
    uiState: HomeUiState,
    isKidMode: Boolean,
    selectedContentMode: ContentMode,
    onContentModeSelected: (ContentMode) -> Unit,
    onNavigateToSearch: (ContentMode) -> Unit,
    onNavigateToSettings: () -> Unit,
    isLandscape: Boolean,
    onVideoClick: (String) -> Unit,
    onChannelClick: (PopularChannel) -> Unit
) {
    val hasContent = uiState.videos.isNotEmpty() ||
            uiState.recentlyWatched.isNotEmpty() ||
            uiState.mostWatched.isNotEmpty() ||
            uiState.favorites.isNotEmpty() ||
            uiState.quickPicks.isNotEmpty() ||
            uiState.moodSections.any { it.videos.isNotEmpty() }

    if (!hasContent && uiState.isLoading) {
        // Show loading shimmer
        HomeShimmer()
    } else if (!hasContent) {
        // Empty state - adaptive based on mode
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeHeader(
                selectedContentMode = selectedContentMode,
                onContentModeSelected = onContentModeSelected,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToSettings = onNavigateToSettings,
                isLandscape = isLandscape
            )

            Spacer(modifier = Modifier.weight(1f))

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

            Spacer(modifier = Modifier.weight(1f))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header scrolls with content
            item {
                HomeHeader(
                    selectedContentMode = selectedContentMode,
                    onContentModeSelected = onContentModeSelected,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToSettings = onNavigateToSettings,
                    isLandscape = isLandscape
                )
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
    selectedContentMode: ContentMode,
    onContentModeSelected: (ContentMode) -> Unit,
    onNavigateToSearch: (ContentMode) -> Unit,
    onNavigateToSettings: () -> Unit,
    isLandscape: Boolean,
    onTrackClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    val hasContent = uiState.sections.isNotEmpty() ||
            uiState.recentlyPlayed.isNotEmpty() ||
            uiState.mostPlayed.isNotEmpty()

    if (!hasContent) {
        // Empty state for music - adaptive based on mode
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeHeader(
                selectedContentMode = selectedContentMode,
                onContentModeSelected = onContentModeSelected,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToSettings = onNavigateToSettings,
                isLandscape = isLandscape
            )

            Spacer(modifier = Modifier.weight(1f))

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

            Spacer(modifier = Modifier.weight(1f))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header scrolls with content
            item {
                HomeHeader(
                    selectedContentMode = selectedContentMode,
                    onContentModeSelected = onContentModeSelected,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToSettings = onNavigateToSettings,
                    isLandscape = isLandscape
                )
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

// ==================== SCROLLABLE HOME HEADER ====================

@Composable
private fun HomeHeader(
    selectedContentMode: ContentMode,
    onContentModeSelected: (ContentMode) -> Unit,
    onNavigateToSearch: (ContentMode) -> Unit,
    onNavigateToSettings: () -> Unit,
    isLandscape: Boolean
) {
    if (isLandscape) {
        // Landscape Compact Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Zimba Beats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                onClick = { onNavigateToSearch(selectedContentMode) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (selectedContentMode == ContentMode.VIDEO) Icons.Default.Search
                        else Icons.Default.MusicNote,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (selectedContentMode == ContentMode.VIDEO)
                            "Search videos..."
                        else
                            "Search music...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ContentModeToggle(
                selectedMode = selectedContentMode,
                onModeSelected = onContentModeSelected
            )

            IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Settings,
                    ContentDescriptions.OPEN_SETTINGS,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        // Portrait Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
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
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
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

            // Aesthetic Video/Music Segmented Toggle
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp)
            ) {
                ContentModeToggle(
                    selectedMode = selectedContentMode,
                    onModeSelected = onContentModeSelected
                )
            }
        }
    }
}

// ==================== AESTHETIC TOGGLE COMPONENTS ====================

@Composable
private fun ContentModeToggle(
    selectedMode: ContentMode,
    onModeSelected: (ContentMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContentModeOption(
                text = "Videos",
                icon = Icons.Default.VideoLibrary,
                isSelected = selectedMode == ContentMode.VIDEO,
                onClick = { onModeSelected(ContentMode.VIDEO) }
            )
            ContentModeOption(
                text = "Music",
                icon = Icons.Default.MusicNote,
                isSelected = selectedMode == ContentMode.MUSIC,
                onClick = { onModeSelected(ContentMode.MUSIC) }
            )
        }
    }
}

@Composable
private fun ContentModeOption(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mode_option_scale"
    )

    val animatedBackgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "mode_option_bg"
    )

    val animatedContentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "mode_option_content"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = animatedBackgroundColor,
        shadowElevation = if (isSelected) 4.dp else 0.dp,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = animatedContentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = animatedContentColor
            )
        }
    }
}

