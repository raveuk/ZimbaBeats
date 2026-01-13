package com.zimbabeats.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.ui.screen.ContentMode
import com.zimbabeats.ui.screen.DownloadsScreen
import com.zimbabeats.ui.screen.FavoritesScreen
import com.zimbabeats.ui.screen.HomeScreen
import com.zimbabeats.ui.screen.LibraryScreen
import com.zimbabeats.ui.screen.ParentalControlScreen
import com.zimbabeats.ui.screen.ParentalDashboardScreen
import com.zimbabeats.ui.screen.PlaylistDetailScreen
import com.zimbabeats.ui.screen.PlaylistsScreen
import com.zimbabeats.ui.screen.ImportPlaylistScreen
import com.zimbabeats.ui.screen.SearchScreen
import com.zimbabeats.ui.screen.SearchMode
import com.zimbabeats.ui.screen.SettingsScreen
import com.zimbabeats.ui.screen.WatchHistoryScreen
import com.zimbabeats.media.music.MusicPlaybackManager
import com.zimbabeats.ui.screen.music.AlbumDetailScreen
import com.zimbabeats.ui.screen.music.ArtistDetailScreen
import com.zimbabeats.ui.screen.music.MusicPlayerScreen
import com.zimbabeats.ui.screen.music.MusicPlaylistDetailScreen
import com.zimbabeats.ui.screen.music.YouTubeMusicPlaylistDetailScreen
import com.zimbabeats.ui.screen.PairingScreen
import com.zimbabeats.ui.screen.player.VideoPlayerScreen
import org.koin.compose.koinInject

@Composable
fun ZimbaBeatsNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    cloudPairingClient: CloudPairingClient = koinInject()
) {
    // Listen for remote unlink by parent
    val unlinkedByParent by cloudPairingClient.unlinkedByParent.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
        navController = navController,
        startDestination = Screen.Home,
        modifier = modifier
    ) {
        // Home screen
        composable<Screen.Home> {
            HomeScreen(
                onNavigateToSearch = { contentMode ->
                    val mode = if (contentMode == ContentMode.VIDEO) "VIDEO" else "MUSIC"
                    navController.navigate(Screen.Search(mode))
                },
                onNavigateToPlaylists = {
                    navController.navigate(Screen.Playlists)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings)
                },
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library)
                },
                onVideoClick = { videoId ->
                    navController.navigate(Screen.VideoPlayer(videoId))
                },
                onTrackClick = { trackId ->
                    navController.navigate(Screen.MusicPlayer(trackId))
                },
                onAlbumClick = { albumId ->
                    navController.navigate(Screen.AlbumDetail(albumId))
                },
                onArtistClick = { artistId ->
                    navController.navigate(Screen.ArtistDetail(artistId))
                },
                onYouTubeMusicPlaylistClick = { playlistId ->
                    navController.navigate(Screen.YouTubeMusicPlaylistDetail(playlistId))
                }
            )
        }

        // Search screen
        composable<Screen.Search> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Search>()
            val initialMode = if (args.mode == "MUSIC") SearchMode.MUSIC else SearchMode.VIDEO
            val musicPlaybackManager: MusicPlaybackManager = koinInject()
            SearchScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVideoClick = { videoId ->
                    navController.navigate(Screen.VideoPlayer(videoId))
                },
                onTrackClick = { trackId ->
                    navController.navigate(Screen.MusicPlayer(trackId))
                },
                onPlayTracks = { tracks, startIndex ->
                    // Set up the queue with search results
                    musicPlaybackManager.playTracks(tracks, startIndex)
                    // Navigate to player with the selected track
                    if (tracks.isNotEmpty()) {
                        val trackId = tracks[startIndex.coerceIn(0, tracks.size - 1)].id
                        navController.navigate(Screen.MusicPlayer(trackId))
                    }
                },
                onArtistClick = { artistId ->
                    navController.navigate(Screen.ArtistDetail(artistId))
                },
                onAlbumClick = { albumId ->
                    navController.navigate(Screen.AlbumDetail(albumId))
                },
                initialSearchMode = initialMode
            )
        }

        // Playlists screen
        composable<Screen.Playlists> {
            PlaylistsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail(playlistId))
                },
                onNavigateToImport = {
                    navController.navigate(Screen.ImportPlaylist)
                }
            )
        }

        // Import Playlist screen
        composable<Screen.ImportPlaylist> {
            ImportPlaylistScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onImportSuccess = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail(playlistId)) {
                        popUpTo(Screen.ImportPlaylist) { inclusive = true }
                    }
                }
            )
        }

        // Video Player screen
        composable<Screen.VideoPlayer> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.VideoPlayer>()
            VideoPlayerScreen(
                videoId = args.videoId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Playlist Detail screen
        composable<Screen.PlaylistDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.PlaylistDetail>()
            PlaylistDetailScreen(
                playlistId = args.playlistId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVideoClick = { videoId ->
                    navController.navigate(Screen.VideoPlayer(videoId))
                },
                onTrackClick = { trackId ->
                    navController.navigate(Screen.MusicPlayer(trackId))
                }
            )
        }

        // Downloads screen
        composable<Screen.Downloads> {
            DownloadsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVideoClick = { videoId ->
                    navController.navigate(Screen.VideoPlayer(videoId))
                }
            )
        }

        // Favorites screen
        composable<Screen.Favorites> {
            FavoritesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVideoClick = { videoId ->
                    navController.navigate(Screen.VideoPlayer(videoId))
                }
            )
        }

        // Watch History screen
        composable<Screen.WatchHistory> {
            WatchHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVideoClick = { videoId ->
                    navController.navigate(Screen.VideoPlayer(videoId))
                }
            )
        }

        // Library screen
        composable<Screen.Library> {
            LibraryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVideoClick = { videoId ->
                    navController.navigate(Screen.VideoPlayer(videoId))
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail(playlistId))
                },
                onNavigateToImport = {
                    navController.navigate(Screen.ImportPlaylist)
                },
                onNavigateToPlaylists = {
                    navController.navigate(Screen.Playlists)
                }
            )
        }

        // Settings screen
        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToParentalControls = {
                    navController.navigate(Screen.ParentalControl)
                }
            )
        }

        // Parental Control screen
        composable<Screen.ParentalControl> {
            ParentalControlScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.ParentalDashboard)
                },
                onNavigateToPairing = {
                    navController.navigate(Screen.Pairing)
                }
            )
        }

        // Parental Dashboard screen
        composable<Screen.ParentalDashboard> {
            ParentalDashboardScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.ParentalControl)
                }
            )
        }

        // Pairing screen (Firebase cross-device pairing)
        composable<Screen.Pairing> {
            PairingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPairingSuccess = {
                    // Go back to parental control screen after successful pairing
                    navController.popBackStack()
                }
            )
        }

        // ==================== Music Screens ====================

        // Music Player screen
        composable<Screen.MusicPlayer> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.MusicPlayer>()
            MusicPlayerScreen(
                trackId = args.trackId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Artist Detail screen
        composable<Screen.ArtistDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.ArtistDetail>()
            ArtistDetailScreen(
                artistId = args.artistId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTrackClick = { trackId ->
                    navController.navigate(Screen.MusicPlayer(trackId))
                }
            )
        }

        // Album Detail screen
        composable<Screen.AlbumDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.AlbumDetail>()
            val musicPlaybackManager: MusicPlaybackManager = koinInject()
            AlbumDetailScreen(
                albumId = args.albumId,
                onNavigateBack = { navController.popBackStack() },
                onTrackClick = { trackId ->
                    navController.navigate(Screen.MusicPlayer(trackId))
                },
                onPlayTracks = { tracks, startIndex ->
                    // Set up the queue with all album tracks
                    musicPlaybackManager.playTracks(tracks, startIndex)
                    // Navigate to player with the selected track
                    if (tracks.isNotEmpty()) {
                        val trackId = tracks[startIndex.coerceIn(0, tracks.size - 1)].id
                        navController.navigate(Screen.MusicPlayer(trackId))
                    }
                },
                onArtistClick = { artistId ->
                    navController.navigate(Screen.ArtistDetail(artistId))
                }
            )
        }

        // Music Playlist Detail screen
        composable<Screen.MusicPlaylistDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.MusicPlaylistDetail>()
            val musicPlaybackManager: MusicPlaybackManager = koinInject()
            MusicPlaylistDetailScreen(
                playlistId = args.playlistId,
                onNavigateBack = { navController.popBackStack() },
                onTrackClick = { trackId ->
                    navController.navigate(Screen.MusicPlayer(trackId))
                },
                onPlayTracks = { tracks, startIndex ->
                    musicPlaybackManager.playTracks(tracks, startIndex)
                    if (tracks.isNotEmpty()) {
                        val trackId = tracks[startIndex.coerceIn(0, tracks.size - 1)].id
                        navController.navigate(Screen.MusicPlayer(trackId))
                    }
                }
            )
        }

        // YouTube Music Playlist Detail screen (for playlists from browse/search)
        composable<Screen.YouTubeMusicPlaylistDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.YouTubeMusicPlaylistDetail>()
            val musicPlaybackManager: MusicPlaybackManager = koinInject()
            YouTubeMusicPlaylistDetailScreen(
                playlistId = args.playlistId,
                onNavigateBack = { navController.popBackStack() },
                onTrackClick = { trackId ->
                    navController.navigate(Screen.MusicPlayer(trackId))
                },
                onPlayTracks = { tracks, startIndex ->
                    musicPlaybackManager.playTracks(tracks, startIndex)
                    if (tracks.isNotEmpty()) {
                        val trackId = tracks[startIndex.coerceIn(0, tracks.size - 1)].id
                        navController.navigate(Screen.MusicPlayer(trackId))
                    }
                }
            )
        }
        }

        // Dialog shown when parent removes this device
        if (unlinkedByParent) {
            AlertDialog(
                onDismissRequest = { /* Cannot dismiss */ },
                icon = {
                    Icon(
                        Icons.Default.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        "Device Unlinked",
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Your parent has removed this device from their family account.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Parental controls are no longer active. Ask your parent to re-link this device if needed.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { cloudPairingClient.clearUnlinkedFlag() }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

/**
 * Placeholder screen for features coming soon
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(
    title: String,
    message: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Construction,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
