package com.zimbabeats.ui.screen.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.data.AppPreferences
import com.zimbabeats.data.AccentColor
import com.zimbabeats.ui.accessibility.AccessibilityAnnouncement
import com.zimbabeats.ui.accessibility.Announcements
import com.zimbabeats.ui.accessibility.ContentDescriptions
import com.zimbabeats.ui.viewmodel.VideoPlayerViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Resize mode options for video player
 */
enum class VideoResizeMode(val label: String, val aspectRatioMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    onNavigateBack: () -> Unit,
    viewModel: VideoPlayerViewModel = koinViewModel { parametersOf(videoId) },
    appPreferences: AppPreferences = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val currentAccentColor by appPreferences.accentColorFlow.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Use the app's accent color
    val accentColor = currentAccentColor.primary

    // Detect landscape mode
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Video resize mode state
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }

    // Controls visibility state
    var showControls by remember { mutableStateOf(true) }

    // Double-tap seek feedback
    var showSeekBackward by remember { mutableStateOf(false) }
    var showSeekForward by remember { mutableStateOf(false) }

    // PlayerView reference for resize mode changes
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // Hide seek feedback after delay
    LaunchedEffect(showSeekBackward) {
        if (showSeekBackward) {
            delay(800)
            showSeekBackward = false
        }
    }

    LaunchedEffect(showSeekForward) {
        if (showSeekForward) {
            delay(800)
            showSeekForward = false
        }
    }

    // Fullscreen immersive mode for landscape
    LaunchedEffect(isLandscape) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window

        if (isLandscape) {
            // Enter immersive mode
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Exit immersive mode
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.apply {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Restore system bars on dispose
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity ?: return@onDispose
            val window = activity.window
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }

    // Announce video title when loaded
    val videoTitle = uiState.video?.title
    AccessibilityAnnouncement(
        message = videoTitle?.let { Announcements.playbackStarted(it) }
    )

    // Announce errors
    AccessibilityAnnouncement(
        message = uiState.error?.let { Announcements.error(it) }
    )

    // Calculate progress
    val progress = if (playerState.duration > 0) {
        playerState.currentPosition.toFloat() / playerState.duration.toFloat()
    } else 0f

    // Fullscreen landscape player
    if (isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls }
                    )
                }
        ) {
            // Video Player - Fullscreen
            val currentResizeMode = resizeMode.aspectRatioMode
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.getPlayer()
                        useController = false // Custom controls
                        setResizeMode(currentResizeMode)
                    }
                },
                update = { view ->
                    view.resizeMode = currentResizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            // Double-tap seek areas
            Row(modifier = Modifier.fillMaxSize()) {
                // Left side - backward seek
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = {
                                    viewModel.seekBackward(5000)
                                    showSeekBackward = true
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {}

                // Right side - forward seek
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = {
                                    viewModel.seekForward(5000)
                                    showSeekForward = true
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {}
            }

            // Seek backward feedback
            if (showSeekBackward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 60.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Replay5,
                                contentDescription = "Seek backward 5s",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Text("5s", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Seek forward feedback
            if (showSeekForward) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 60.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Forward5,
                                contentDescription = "Seek forward 5s",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Text("5s", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Controls overlay
            if (showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    // Top bar with back button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                )
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            viewModel.saveProgress()
                            onNavigateBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Resize mode button
                        Surface(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    VideoResizeMode.FIT -> VideoResizeMode.FILL
                                    VideoResizeMode.FILL -> VideoResizeMode.ZOOM
                                    VideoResizeMode.ZOOM -> VideoResizeMode.FIT
                                }
                            },
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = resizeMode.label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Center play/pause button
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    // Bottom bar with progress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Progress bar
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = accentColor,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Time display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(playerState.currentPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                formatTime(playerState.duration),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
        return
    }

    // Portrait mode - original layout
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveProgress()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, ContentDescriptions.NAVIGATE_BACK)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            "Error",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            uiState.error!!,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Video Player - Fixed at top, doesn't scroll
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = viewModel.getPlayer()
                                    useController = true
                                    controllerShowTimeoutMs = 3000
                                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        )

                        // Action Buttons - Fixed below player, doesn't scroll
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { contentDescription = "Video actions" },
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val favoriteDescription = if (uiState.isFavorite)
                                ContentDescriptions.REMOVE_FROM_FAVORITES
                            else
                                ContentDescriptions.ADD_TO_FAVORITES

                            IconButton(onClick = { viewModel.toggleFavorite() }) {
                                Icon(
                                    if (uiState.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    favoriteDescription,
                                    tint = if (uiState.isFavorite) Color(0xFFE91E63) else Color.White
                                )
                            }

                            IconButton(onClick = { viewModel.showPlaylistPicker() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistAdd,
                                    ContentDescriptions.ADD_TO_PLAYLIST,
                                    tint = Color.White
                                )
                            }

                            // Download button with state
                            if (uiState.canDownload) {
                                val downloadDescription = when {
                                    uiState.isLoadingDownloadSize -> "Loading download options"
                                    uiState.downloadState == com.zimbabeats.ui.viewmodel.DownloadButtonState.DOWNLOADING ->
                                        "Downloading, ${uiState.downloadProgress} percent complete"
                                    uiState.downloadState == com.zimbabeats.ui.viewmodel.DownloadButtonState.COMPLETED ->
                                        "Video downloaded"
                                    else -> ContentDescriptions.DOWNLOAD_VIDEO
                                }

                                IconButton(
                                    onClick = { viewModel.requestDownload() },
                                    enabled = uiState.downloadState != com.zimbabeats.ui.viewmodel.DownloadButtonState.DOWNLOADING &&
                                            !uiState.isLoadingDownloadSize,
                                    modifier = Modifier.semantics {
                                        contentDescription = downloadDescription
                                    }
                                ) {
                                    when {
                                        uiState.isLoadingDownloadSize -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        uiState.downloadState == com.zimbabeats.ui.viewmodel.DownloadButtonState.DOWNLOADING -> {
                                            CircularProgressIndicator(
                                                progress = { uiState.downloadProgress / 100f },
                                                modifier = Modifier.size(24.dp),
                                                color = accentColor,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        uiState.downloadState == com.zimbabeats.ui.viewmodel.DownloadButtonState.COMPLETED -> {
                                            Icon(
                                                Icons.Default.DownloadDone,
                                                null, // Handled by parent semantics
                                                tint = accentColor
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                Icons.Default.Download,
                                                null, // Handled by parent semantics
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            // Share button - clean URL only
                            if (uiState.canShare) {
                                val shareUrl = viewModel.getShareUrl()
                                IconButton(
                                    onClick = {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, shareUrl)
                                        }
                                        val chooserIntent = android.content.Intent.createChooser(shareIntent, "Share URL")
                                        context.startActivity(chooserIntent)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        ContentDescriptions.SHARE_VIDEO,
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        // Scrollable content below the player and action buttons
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // Video Info (channel, views, description)
                            uiState.video?.let { video ->
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = video.channelName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )

                                        video.description?.let { description ->
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 3
                                            )
                                        }
                                    }
                                }
                            }

                            // Related Videos Section
                            if (uiState.relatedVideos.isNotEmpty() || uiState.isLoadingRelated) {
                                item {
                                    Text(
                                        text = "More from ${uiState.video?.channelName ?: "this channel"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }

                                if (uiState.isLoadingRelated) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = Color.White)
                                        }
                                    }
                                } else {
                                    items(uiState.relatedVideos) { relatedVideo ->
                                        RelatedVideoItem(
                                            video = relatedVideo,
                                            onClick = {
                                                // Navigate to the related video
                                                viewModel.saveProgress()
                                                viewModel.loadVideo(relatedVideo.id)
                                            }
                                        )
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
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
            accentColor = accentColor,
            onPlaylistSelected = { playlistId ->
                viewModel.addToPlaylist(playlistId)
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylistAndAddVideo(name)
            },
            onDismiss = { viewModel.hidePlaylistPicker() }
        )
    }

    // Download confirmation dialog with quality selection
    if (uiState.showDownloadConfirmation) {
        DownloadQualityDialog(
            videoTitle = uiState.video?.title ?: "",
            availableQualities = uiState.availableDownloadQualities,
            selectedQuality = uiState.selectedDownloadQuality,
            accentColor = accentColor,
            onQualitySelected = { viewModel.selectDownloadQuality(it) },
            onConfirm = { viewModel.confirmDownload() },
            onDismiss = { viewModel.dismissDownloadConfirmation() }
        )
    }

    // Save progress when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveProgress()
        }
    }
}

@Composable
fun QualitySelectionDialog(
    availableQualities: List<com.zimbabeats.ui.viewmodel.QualityOption>,
    currentQuality: String,
    onQualitySelected: (com.zimbabeats.ui.viewmodel.QualityOption) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Select Quality",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                availableQualities.forEach { quality ->
                    val isSelected = quality.quality == currentQuality

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color = if (isSelected) Color(0xFF404040) else Color.Transparent,
                        shape = MaterialTheme.shapes.small,
                        onClick = { onQualitySelected(quality) }
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
                                    text = quality.quality,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = quality.format.uppercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    accentColor: Color = Color(0xFF1DB954),
    onPlaylistSelected: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            accentColor = accentColor,
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Add to Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Create New Playlist Button - Always visible at top
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color = accentColor.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small,
                        onClick = { showCreateDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                color = accentColor,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }

                            Text(
                                text = "Create New Playlist",
                                style = MaterialTheme.typography.bodyLarge,
                                color = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Your Playlists",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 250.dp)
                        ) {
                            items(playlists) { playlist ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    color = Color.Transparent,
                                    shape = MaterialTheme.shapes.small,
                                    onClick = { onPlaylistSelected(playlist.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Color indicator
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            color = Color(android.graphics.Color.parseColor(playlist.color.hex)),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = playlist.videoCount.toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${playlist.videoCount} videos",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }

                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Add",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    accentColor: Color = Color(0xFF1DB954),
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Text(
                "Create New Playlist",
                color = Color.White
            )
        },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = Color.Gray
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(playlistName) },
                enabled = playlistName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor
                )
            ) {
                Text("Create & Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White)
            }
        }
    )
}

@Composable
fun DownloadQualityDialog(
    videoTitle: String,
    availableQualities: List<com.zimbabeats.download.DownloadQualityOption>,
    selectedQuality: com.zimbabeats.download.DownloadQualityOption?,
    accentColor: Color = Color(0xFF1DB954),
    onQualitySelected: (com.zimbabeats.download.DownloadQualityOption) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Download Video",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Video title
                Text(
                    text = videoTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Quality label
                Text(
                    text = "Select Quality",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quality options
                if (availableQualities.isEmpty()) {
                    Text(
                        text = "No downloadable qualities available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFAB00)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 250.dp)
                    ) {
                        items(availableQualities) { quality ->
                            val isSelected = selectedQuality == quality

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onQualitySelected(quality) },
                                color = if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0xFF2A2A2A),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Radio button indicator
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(
                                                    color = if (isSelected) accentColor else Color.Transparent,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .then(
                                                    if (!isSelected) Modifier.background(
                                                        color = Color.Transparent,
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .background(
                                                            color = Color.Transparent,
                                                            shape = RoundedCornerShape(9.dp)
                                                        )
                                                        .padding(1.dp)
                                                        .background(
                                                            color = Color.Gray,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = quality.quality,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (isSelected) accentColor else Color.White
                                            )
                                            Text(
                                                text = quality.format.uppercase(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    // Size info
                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = quality.sizeFormatted,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) accentColor else Color.White
                                        )
                                        // Show recommended for highest quality
                                        if (quality == availableQualities.firstOrNull()) {
                                            Text(
                                                text = "Recommended",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = selectedQuality != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}

/**
 * Related video item displayed under the player
 */
@Composable
private fun RelatedVideoItem(
    video: Video,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .size(width = 120.dp, height = 68.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            // Video info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
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
}

/**
 * Format time in milliseconds to MM:SS or HH:MM:SS
 */
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
