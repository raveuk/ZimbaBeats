package com.zimbabeats.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.core.data.remote.youtube.YouTubeHistorySync
import com.zimbabeats.core.data.remote.youtube.YouTubeService
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.PlaylistColor
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.VideoQuality
import com.zimbabeats.core.domain.repository.DownloadRepository
import com.zimbabeats.core.domain.repository.PlaylistRepository
import com.zimbabeats.core.domain.repository.UsageRepository
import com.zimbabeats.core.domain.repository.VideoRepository
import com.zimbabeats.core.domain.util.Resource
import com.zimbabeats.data.AppPreferences
import com.zimbabeats.download.DownloadQualityOption
import com.zimbabeats.download.DownloadSizeInfo
import com.zimbabeats.media.player.ZimbaBeatsPlayer
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PairingStatus
import com.zimbabeats.core.domain.filter.VideoContentFilter
import com.zimbabeats.core.domain.filter.AgeGroup
import com.zimbabeats.core.domain.filter.FilterResult
import com.zimbabeats.core.domain.model.AgeRating
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QualityOption(
    val quality: String,
    val url: String,
    val format: String,
    val isVideoOnly: Boolean
)

enum class DownloadButtonState {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    DISABLED
}

data class VideoPlayerUiState(
    val video: Video? = null,
    val isLoading: Boolean = true,
    val streamUrl: String? = null,
    val error: String? = null,
    val availableQualities: List<QualityOption> = emptyList(),
    val currentQuality: String = "auto",
    val showQualitySelector: Boolean = false,
    val isFavorite: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val showPlaylistPicker: Boolean = false,
    val downloadState: DownloadButtonState = DownloadButtonState.IDLE,
    val downloadProgress: Int = 0,
    val canDownload: Boolean = true,
    val canShare: Boolean = true,
    // Download confirmation with quality selection
    val showDownloadConfirmation: Boolean = false,
    val downloadSizeInfo: DownloadSizeInfo? = null,
    val isLoadingDownloadSize: Boolean = false,
    val availableDownloadQualities: List<DownloadQualityOption> = emptyList(),
    val selectedDownloadQuality: DownloadQualityOption? = null,
    // Related videos from same channel
    val relatedVideos: List<Video> = emptyList(),
    val isLoadingRelated: Boolean = false,
    // Offline playback indicator
    val isPlayingOffline: Boolean = false
)

class VideoPlayerViewModel(
    application: Application,
    private val videoRepository: VideoRepository,
    private val youTubeService: YouTubeService,
    private val usageRepository: UsageRepository,
    private val playlistRepository: PlaylistRepository,
    private val youTubeHistorySync: YouTubeHistorySync,
    private val downloadManager: com.zimbabeats.download.DownloadManager,
    private val downloadRepository: DownloadRepository,
    private val cloudPairingClient: CloudPairingClient,
    private val appPreferences: AppPreferences,
    private val videoContentFilter: VideoContentFilter
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VideoPlayerViewModel"
    }

    private val player = ZimbaBeatsPlayer(application)
    private var loadedVideoId: String? = null  // Track which video is loaded

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    val playerState = player.playerState

    private var sessionId: Long? = null
    private var watchStartTime: Long = 0

    init {
        // Load playlists for playlist picker
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }

        // Observe cloud pairing state for download permissions
        viewModelScope.launch {
            cloudPairingClient.pairingStatus.collect { pairingStatus ->
                updatePermissions(pairingStatus)
            }
        }
    }

    /**
     * Update permissions based on cloud pairing state.
     * Downloads are always allowed for now - parental control only filters content.
     * Future: Could add download restrictions to CloudFilterSettings.
     */
    private fun updatePermissions(pairingStatus: PairingStatus) {
        // Downloads always allowed - cloud filter handles content blocking
        // Future enhancement: add downloadRestricted to CloudFilterSettings
        _uiState.value = _uiState.value.copy(
            canDownload = true,
            canShare = true
        )
    }

    fun loadVideo(videoId: String) {
        // Prevent loading the same video multiple times
        if (loadedVideoId == videoId) {
            Log.d(TAG, "Video $videoId already loaded, skipping")
            return
        }

        // Reset state for new video
        loadedVideoId = videoId
        _uiState.value = VideoPlayerUiState(isLoading = true)

        Log.d(TAG, "Loading video: $videoId")

        // Observe favorite status
        viewModelScope.launch {
            videoRepository.isVideoFavorite(videoId).collect { isFavorite ->
                _uiState.value = _uiState.value.copy(isFavorite = isFavorite)
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Get video info (use .first() to get single value, not continuous collection)
            val video = videoRepository.getVideoById(videoId).first()

            if (video != null) {
                Log.d(TAG, "Video info loaded: ${video.title}")
                _uiState.value = _uiState.value.copy(video = video)

                // CHECK FOR DOWNLOADED CONTENT FIRST (Bug 8 fix - offline playback)
                val localFilePath = downloadManager.getDownloadedFilePath(videoId)
                if (localFilePath != null) {
                    Log.d(TAG, "Playing from local downloaded file: $localFilePath")

                    val localFileUri = "file://$localFilePath"
                    _uiState.value = _uiState.value.copy(
                        streamUrl = localFileUri,
                        availableQualities = listOf(
                            QualityOption(
                                quality = "Downloaded",
                                url = localFileUri,
                                format = "mp4",
                                isVideoOnly = false
                            )
                        ),
                        currentQuality = "Downloaded",
                        isLoading = false,
                        isPlayingOffline = true,
                        downloadState = DownloadButtonState.COMPLETED
                    )

                    player.playVideo(localFileUri, video.title)
                    startWatchSession(videoId)
                    saveWatchHistoryNow()
                    loadRelatedVideos(video.channelId, videoId)
                    return@launch
                }

                // Not downloaded - Get stream URLs from YouTube
                try {
                    Log.d(TAG, "Fetching stream URLs...")
                    val streams = youTubeService.getStreamUrls(videoId)
                    Log.d(TAG, "Got ${streams.size} stream URLs")
                    streams.forEach {
                        Log.d(TAG, "  - ${it.quality} (${it.format}) - videoOnly: ${it.isVideoOnly}, url: ${it.url.take(100)}...")
                    }

                    // Get user's preferred quality setting
                    val maxQuality = appPreferences.getMaxQualityValue()
                    val preferredQualitySetting = appPreferences.getPreferredQuality()
                    Log.d(TAG, "User preferred quality: $preferredQualitySetting (max: ${maxQuality ?: "unlimited"})")

                    // Get combined video+audio streams (mp4/webm)
                    // Sorted by resolution - highest first (2160p > 1440p > 1080p > 720p > etc)
                    val combinedStreams = streams
                        .filter { !it.isVideoOnly && !it.quality.contains("kbps") && !it.quality.contains("video-only") && it.format != "hls" }
                        .sortedByDescending { stream ->
                            stream.quality.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                        }

                    // HLS adaptive stream (fallback)
                    val hlsStream = streams.find { it.format == "hls" }

                    // Convert to QualityOption list (highest quality first)
                    val qualityOptions = mutableListOf<QualityOption>()
                    qualityOptions.addAll(combinedStreams.map { stream ->
                        QualityOption(
                            quality = stream.quality,
                            url = stream.url,
                            format = stream.format,
                            isVideoOnly = stream.isVideoOnly
                        )
                    })
                    hlsStream?.let {
                        qualityOptions.add(QualityOption(
                            quality = "Auto (HLS)",
                            url = it.url,
                            format = it.format,
                            isVideoOnly = false
                        ))
                    }

                    // Select stream based on user preference
                    val bestStream = if (maxQuality != null) {
                        // User selected a specific quality - find best match at or below their preference
                        combinedStreams.firstOrNull { stream ->
                            val streamQuality = stream.quality.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                            streamQuality <= maxQuality
                        } ?: combinedStreams.lastOrNull() ?: hlsStream
                    } else {
                        // Auto mode - use highest quality available
                        combinedStreams.firstOrNull() ?: hlsStream
                    }

                    Log.d(TAG, "Available qualities: ${combinedStreams.map { it.quality }}")
                    Log.d(TAG, "Selected quality: ${bestStream?.quality ?: "none"} (preference: $preferredQualitySetting)")

                    if (bestStream != null) {
                        Log.d(TAG, "Using stream: ${bestStream.quality} - ${bestStream.format}")
                        Log.d(TAG, "Stream URL: ${bestStream.url.take(200)}...")
                        Log.d(TAG, "Available qualities: ${qualityOptions.map { it.quality }}")

                        _uiState.value = _uiState.value.copy(
                            streamUrl = bestStream.url,
                            availableQualities = qualityOptions,
                            currentQuality = if (hlsStream != null) "Auto (HLS)" else bestStream.quality,
                            isLoading = false,
                            isPlayingOffline = false
                        )

                        player.playVideo(bestStream.url, video.title)
                        startWatchSession(videoId)

                        // Save to watch history immediately
                        saveWatchHistoryNow()

                        // Fetch related videos from same channel
                        loadRelatedVideos(video.channelId, videoId)
                    } else {
                        Log.e(TAG, "No playable stream found")
                        _uiState.value = _uiState.value.copy(
                            error = "No playable stream found. Please try another video.",
                            isLoading = false
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load video", e)

                    // If streaming failed, check if we have a download as fallback
                    val fallbackPath = downloadManager.getDownloadedFilePath(videoId)
                    if (fallbackPath != null) {
                        Log.d(TAG, "Streaming failed, using downloaded file as fallback: $fallbackPath")
                        val localFileUri = "file://$fallbackPath"
                        _uiState.value = _uiState.value.copy(
                            streamUrl = localFileUri,
                            availableQualities = listOf(
                                QualityOption(
                                    quality = "Downloaded",
                                    url = localFileUri,
                                    format = "mp4",
                                    isVideoOnly = false
                                )
                            ),
                            currentQuality = "Downloaded",
                            isLoading = false,
                            isPlayingOffline = true,
                            downloadState = DownloadButtonState.COMPLETED
                        )
                        player.playVideo(localFileUri, video.title)
                        startWatchSession(videoId)
                        saveWatchHistoryNow()
                        loadRelatedVideos(video.channelId, videoId)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load video: ${e.message}",
                            isLoading = false
                        )
                    }
                }
            } else {
                Log.e(TAG, "Video not found in database")
                _uiState.value = _uiState.value.copy(
                    error = "Video not found",
                    isLoading = false
                )
            }
        }
    }

    /**
     * Load related videos from the same channel using HYBRID APPROACH:
     * 1. Try database first (fast, cached videos from this channel)
     * 2. If < 5 videos, supplement with YouTube API search
     * 3. Apply dual-layer filtering (CloudContentFilter + VideoContentFilter)
     *
     * This ensures:
     * - Fast loading from database when available
     * - Complete results with YouTube API fallback
     * - Age-appropriate content filtering
     */
    private fun loadRelatedVideos(channelId: String, currentVideoId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRelated = true)

            try {
                Log.d(TAG, "Loading related videos for channel: $channelId")

                // STEP 1: Query database for videos from this channel
                val dbVideos = videoRepository.getVideosByChannel(channelId)
                    .first() // Get single snapshot, not continuous flow
                    .filter { it.id != currentVideoId } // Exclude current video

                Log.d(TAG, "Found ${dbVideos.size} videos in database for channel: $channelId")

                // STEP 2: Determine if we need YouTube API fallback
                val needsYouTubeFallback = dbVideos.size < 5

                val allVideos = if (needsYouTubeFallback) {
                    Log.d(TAG, "Database has < 5 videos, fetching from YouTube API as supplement")

                    // Get channel name for search (fallback to empty if not available)
                    val channelName = _uiState.value.video?.channelName ?: ""

                    if (channelName.isEmpty()) {
                        Log.w(TAG, "Channel name not available, using database results only")
                        dbVideos
                    } else {
                        // Fetch from YouTube API
                        // Note: We use channelName search because Innertube doesn't have a channel-specific
                        // search endpoint, but we filter results strictly by channelId to ensure accuracy
                        val youtubeResults = youTubeService.searchVideos(channelName, maxResults = 100)

                        Log.d(TAG, "YouTube API returned ${youtubeResults.size} results for '$channelName'")

                        // Convert YouTubeVideo to Video domain model
                        val youtubeVideos = youtubeResults
                            .filter { it.id != currentVideoId } // Exclude current video
                            .filter { it.channelId == channelId } // STRICT: Only exact channelId match
                            .map { ytVideo ->
                                Video(
                                    id = ytVideo.id,
                                    title = ytVideo.title,
                                    description = ytVideo.description,
                                    thumbnailUrl = ytVideo.thumbnailUrl,
                                    channelName = ytVideo.channelName,
                                    channelId = ytVideo.channelId,
                                    duration = ytVideo.duration,
                                    viewCount = ytVideo.viewCount,
                                    publishedAt = ytVideo.publishedAt,
                                    isKidFriendly = ytVideo.isFamilySafe || ytVideo.isMadeForKids,
                                    category = null
                                )
                            }

                        Log.d(TAG, "Filtered to ${youtubeVideos.size} videos from exact channel")

                        // Combine database + YouTube results, remove duplicates
                        val combined = (dbVideos + youtubeVideos).distinctBy { it.id }
                        Log.d(TAG, "Combined results: ${combined.size} unique videos")
                        combined
                    }
                } else {
                    // Database has enough videos, use them
                    Log.d(TAG, "Database has sufficient videos, using database results only")
                    dbVideos
                }

                // STEP 3: Apply dual-layer filtering
                val filteredVideos = filterRelatedVideos(allVideos)

                // STEP 4: Take top 10 for display
                val finalVideos = filteredVideos.take(10)

                Log.d(TAG, "Loaded ${finalVideos.size} related videos from channel (${filteredVideos.size} after filtering)")

                _uiState.value = _uiState.value.copy(
                    relatedVideos = finalVideos,
                    isLoadingRelated = false
                )

                // STEP 5: Cache the filtered YouTube results for future use
                // Only cache videos that passed filtering to keep database clean
                if (needsYouTubeFallback && filteredVideos.isNotEmpty()) {
                    videoRepository.saveVideos(filteredVideos)
                    Log.d(TAG, "Cached ${filteredVideos.size} filtered videos to database")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load related videos for channel '$channelId'", e)
                _uiState.value = _uiState.value.copy(
                    relatedVideos = emptyList(),
                    isLoadingRelated = false
                )
            }
        }
    }

    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun seekForward() = player.seekForward()
    fun seekBackward() = player.seekBackward()
    fun getPlayer() = player.getPlayer()

    fun togglePlayPause() {
        if (playerState.value.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekForward(millis: Long) {
        val currentPosition = player.getCurrentPosition()
        val duration = player.getDuration()
        val newPosition = (currentPosition + millis).coerceAtMost(duration)
        player.seekTo(newPosition)
    }

    fun seekBackward(millis: Long) {
        val currentPosition = player.getCurrentPosition()
        val newPosition = (currentPosition - millis).coerceAtLeast(0)
        player.seekTo(newPosition)
    }

    fun showQualitySelector() {
        _uiState.value = _uiState.value.copy(showQualitySelector = true)
    }

    fun hideQualitySelector() {
        _uiState.value = _uiState.value.copy(showQualitySelector = false)
    }

    fun switchQuality(qualityOption: QualityOption) {
        val video = _uiState.value.video ?: return
        val currentPosition = player.getCurrentPosition()

        Log.d(TAG, "Switching quality to: ${qualityOption.quality}")

        // Update UI state
        _uiState.value = _uiState.value.copy(
            streamUrl = qualityOption.url,
            currentQuality = qualityOption.quality,
            showQualitySelector = false
        )

        // Switch stream in player
        player.playVideo(qualityOption.url, video.title)

        // Restore playback position
        player.seekTo(currentPosition)
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val video = _uiState.value.video ?: return@launch
            val result = videoRepository.toggleFavorite(video.id)

            if (result is Resource.Error) {
                Log.e(TAG, "Failed to toggle favorite: ${result.message}")
            } else {
                Log.d(TAG, "Favorite toggled for video: ${video.id}")
            }
        }
    }

    fun showPlaylistPicker() {
        _uiState.value = _uiState.value.copy(showPlaylistPicker = true)
    }

    fun hidePlaylistPicker() {
        _uiState.value = _uiState.value.copy(showPlaylistPicker = false)
    }

    fun addToPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val videoId = _uiState.value.video?.id ?: return@launch
            val result = playlistRepository.addVideoToPlaylist(playlistId, videoId)

            if (result is Resource.Error) {
                Log.e(TAG, "Failed to add to playlist: ${result.message}")
            } else {
                Log.d(TAG, "Video $videoId added to playlist $playlistId")
            }
            hidePlaylistPicker()
        }
    }

    /**
     * Create a new playlist and add the current video to it
     */
    fun createPlaylistAndAddVideo(name: String, color: PlaylistColor = PlaylistColor.entries.random()) {
        viewModelScope.launch {
            val videoId = _uiState.value.video?.id ?: return@launch

            // Create the playlist
            val createResult = playlistRepository.createPlaylist(
                name = name,
                description = null,
                color = color
            )

            when (createResult) {
                is Resource.Success -> {
                    val playlistId = createResult.data
                    Log.d(TAG, "Created playlist $playlistId: $name")

                    // Add the video to the new playlist
                    val addResult = playlistRepository.addVideoToPlaylist(playlistId, videoId)
                    if (addResult is Resource.Success) {
                        Log.d(TAG, "Video $videoId added to new playlist $playlistId")
                    } else {
                        Log.e(TAG, "Failed to add video to new playlist")
                    }
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to create playlist: ${createResult.message}")
                }
                else -> {}
            }
            hidePlaylistPicker()
        }
    }

    private fun startWatchSession(videoId: String) {
        viewModelScope.launch {
            watchStartTime = System.currentTimeMillis()
            when (val result = usageRepository.startSession()) {
                is Resource.Success -> {
                    sessionId = result.data
                }
                else -> {}
            }

            // Update last accessed
            videoRepository.updateLastAccessed(videoId)
        }
    }

    fun saveProgress() {
        viewModelScope.launch {
            val video = _uiState.value.video ?: return@launch
            val position = player.getCurrentPosition()
            val duration = player.getDuration()

            if (duration > 0) {
                videoRepository.saveVideoProgress(video.id, position, duration)
            }
        }
    }

    /**
     * Request download - fetches available qualities and shows confirmation dialog
     */
    fun requestDownload() {
        val video = _uiState.value.video ?: return

        if (!_uiState.value.canDownload) {
            Log.d(TAG, "Download not allowed by parental settings")
            return
        }

        // Check if already downloaded
        if (downloadManager.isDownloaded(video.id)) {
            _uiState.value = _uiState.value.copy(downloadState = DownloadButtonState.COMPLETED)
            return
        }

        // Fetch available qualities
        _uiState.value = _uiState.value.copy(isLoadingDownloadSize = true)

        viewModelScope.launch {
            val qualities = downloadManager.getAvailableQualities(video.id)
            Log.d(TAG, "Available download qualities: ${qualities.map { "${it.quality} - ${it.sizeFormatted}" }}")

            // Select highest quality by default
            val defaultQuality = qualities.firstOrNull()

            _uiState.value = _uiState.value.copy(
                isLoadingDownloadSize = false,
                availableDownloadQualities = qualities,
                selectedDownloadQuality = defaultQuality,
                showDownloadConfirmation = true
            )
        }
    }

    /**
     * Select a download quality
     */
    fun selectDownloadQuality(quality: DownloadQualityOption) {
        _uiState.value = _uiState.value.copy(selectedDownloadQuality = quality)
    }

    /**
     * Dismiss download confirmation dialog
     */
    fun dismissDownloadConfirmation() {
        _uiState.value = _uiState.value.copy(
            showDownloadConfirmation = false,
            availableDownloadQualities = emptyList(),
            selectedDownloadQuality = null
        )
    }

    /**
     * Confirm and start downloading the current video with selected quality
     */
    fun confirmDownload() {
        val video = _uiState.value.video ?: return
        val selectedQuality = _uiState.value.selectedDownloadQuality

        if (selectedQuality == null) {
            Log.e(TAG, "No quality selected for download")
            return
        }

        // Hide confirmation dialog
        _uiState.value = _uiState.value.copy(
            showDownloadConfirmation = false,
            availableDownloadQualities = emptyList(),
            selectedDownloadQuality = null,
            downloadState = DownloadButtonState.DOWNLOADING
        )

        Log.d(TAG, "Starting download for: ${video.title} at quality: ${selectedQuality.quality}")

        // Queue the download in repository first, then start WorkManager
        viewModelScope.launch {
            val videoQuality = when {
                selectedQuality.qualityValue >= 1080 -> VideoQuality.HD_1080P
                selectedQuality.qualityValue >= 720 -> VideoQuality.HD_720P
                selectedQuality.qualityValue >= 480 -> VideoQuality.SD_480P
                else -> VideoQuality.SD_360P
            }

            val queueResult = downloadRepository.queueDownload(video.id, videoQuality)
            when (queueResult) {
                is Resource.Success -> {
                    Log.d(TAG, "Download queued successfully for: ${video.id} at ${selectedQuality.quality}")
                    // Use the specific stream URL with network preference
                    val requireWifiOnly = !appPreferences.isMobileDataDownloadAllowed()
                    downloadManager.downloadVideoWithUrl(video.id, selectedQuality.url, selectedQuality.quality, requireWifiOnly)
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to queue download: ${queueResult.message}")
                    _uiState.value = _uiState.value.copy(
                        downloadState = DownloadButtonState.IDLE,
                        downloadProgress = 0
                    )
                    return@launch
                }
                else -> {}
            }
        }

        // Observe download progress
        viewModelScope.launch {
            downloadManager.getDownloadProgress(video.id).collect { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)
            }
        }

        viewModelScope.launch {
            downloadManager.getDownloadWorkInfo(video.id).collect { workInfo ->
                when (workInfo?.state) {
                    androidx.work.WorkInfo.State.SUCCEEDED -> {
                        _uiState.value = _uiState.value.copy(
                            downloadState = DownloadButtonState.COMPLETED,
                            downloadProgress = 100
                        )
                    }
                    androidx.work.WorkInfo.State.FAILED,
                    androidx.work.WorkInfo.State.CANCELLED -> {
                        _uiState.value = _uiState.value.copy(
                            downloadState = DownloadButtonState.IDLE,
                            downloadProgress = 0
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Get sanitized share URL (removes tracking params)
     * ZimbaBeats style - clean URL only, no title
     */
    fun getShareUrl(): String {
        val video = _uiState.value.video ?: return ""
        // Use clean YouTube URL without tracking parameters
        return "https://youtu.be/${video.id}"
    }

    /**
     * Get the cloud content filter (Firebase-based)
     */
    private val contentFilter get() = cloudPairingClient.contentFilter

    /**
     * Get current age rating from cloud settings
     * Returns AgeRating.ALL if not linked to family or settings unavailable
     */
    private fun getCurrentAgeRating(): AgeRating {
        val cloudSettings = cloudPairingClient.cloudSettings.value
        val ageRatingString = cloudSettings?.ageRating
        return parseAgeRating(ageRatingString)
    }

    /**
     * Convert cloud age rating string to AgeRating enum
     */
    private fun parseAgeRating(ageRatingString: String?): AgeRating {
        return when (ageRatingString) {
            "FIVE_PLUS" -> AgeRating.FIVE_PLUS
            "EIGHT_PLUS" -> AgeRating.EIGHT_PLUS
            // Legacy mappings (map to closest)
            "TEN_PLUS" -> AgeRating.EIGHT_PLUS
            "TWELVE_PLUS" -> AgeRating.THIRTEEN_PLUS
            "THIRTEEN_PLUS" -> AgeRating.THIRTEEN_PLUS
            "FOURTEEN_PLUS" -> AgeRating.THIRTEEN_PLUS
            "SIXTEEN_PLUS" -> AgeRating.SIXTEEN_PLUS
            else -> AgeRating.ALL
        }
    }

    /**
     * Convert AgeRating to AgeGroup for local VideoContentFilter
     */
    private fun ageRatingToAgeGroup(ageRating: AgeRating): AgeGroup {
        return when (ageRating) {
            AgeRating.ALL -> AgeGroup.UNDER_16
            AgeRating.FIVE_PLUS -> AgeGroup.UNDER_5
            AgeRating.EIGHT_PLUS -> AgeGroup.UNDER_8
            AgeRating.THIRTEEN_PLUS -> AgeGroup.UNDER_13
            AgeRating.SIXTEEN_PLUS -> AgeGroup.UNDER_16
        }
    }

    /**
     * Apply dual-layer filtering to related videos
     * Same pattern as HomeViewModel lines 896-944
     */
    private suspend fun filterRelatedVideos(videos: List<Video>): List<Video> = withContext(Dispatchers.Default) {
        // If not linked to family, allow all videos (unrestricted mode)
        val filter = contentFilter
        if (filter == null) {
            Log.d(TAG, "Not linked to family - unrestricted mode, allowing all ${videos.size} videos")
            return@withContext videos
        }

        val currentAgeLevel = getCurrentAgeRating()
        val ageGroup = ageRatingToAgeGroup(currentAgeLevel)
        val useStrictLocalFilter = currentAgeLevel in listOf(
            AgeRating.FIVE_PLUS, AgeRating.EIGHT_PLUS
        )

        Log.d(TAG, "Filtering ${videos.size} related videos - Age: ${currentAgeLevel.displayName}, Strict local filter: $useStrictLocalFilter")

        val filteredVideos = videos.filter { video ->
            // LAYER 1: Cloud Content Filter (Firebase-based)
            val durationSeconds = if (video.duration > 100000) video.duration / 1000 else video.duration
            val blockResult = filter.shouldBlockContent(
                videoId = video.id,
                title = video.title,
                channelId = video.channelId,
                channelName = video.channelName,
                description = video.description,
                durationSeconds = durationSeconds,
                isLiveStream = false,
                category = video.category?.name
            )
            if (blockResult.isBlocked) {
                Log.d(TAG, "CLOUD BLOCKED related video: '${video.title}' - ${blockResult.message}")
                return@filter false
            }

            // LAYER 2: Local VideoContentFilter with strictMode (for young kids)
            if (useStrictLocalFilter) {
                val localResult = videoContentFilter.filterVideo(video, ageGroup)
                if (!localResult.allowed) {
                    Log.d(TAG, "LOCAL BLOCKED related video: '${video.title}' - ${localResult.reason} (score: ${localResult.score})")
                    return@filter false
                }
            }

            true
        }

        Log.d(TAG, "Filtered related videos: ${filteredVideos.size}/${videos.size} passed (${videos.size - filteredVideos.size} blocked)")
        filteredVideos
    }

    /**
     * Check if video is already downloaded
     */
    fun checkDownloadStatus() {
        val video = _uiState.value.video ?: return
        if (downloadManager.isDownloaded(video.id)) {
            _uiState.value = _uiState.value.copy(downloadState = DownloadButtonState.COMPLETED)
        }
    }

    /**
     * Save watch history immediately (called when video starts playing)
     * This ensures history is saved even if user force-closes the app
     */
    fun saveWatchHistoryNow() {
        val video = _uiState.value.video ?: return
        viewModelScope.launch {
            try {
                // Save locally
                videoRepository.addToWatchHistory(
                    videoId = video.id,
                    watchDuration = 0,
                    completionPercentage = 0f,
                    profileId = null
                )
                Log.d(TAG, "Watch history saved for: ${video.id}")

                // Sync to Firebase for parent to view
                cloudPairingClient.syncWatchHistory(
                    videoId = video.id,
                    title = video.title,
                    channelName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = video.duration
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save watch history", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel being cleared - saving progress and releasing player")

        // Use a non-cancellable scope to ensure history is saved
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val video = _uiState.value.video
        val currentSessionId = sessionId
        val currentWatchStartTime = watchStartTime
        val currentPosition = player.getCurrentPosition()
        val duration = player.getDuration()

        // Save watch history in a scope that won't be cancelled
        cleanupScope.launch {
            if (video != null) {
                val watchDuration = (System.currentTimeMillis() - currentWatchStartTime) / 1000
                val totalDuration = duration / 1000
                val completionPercentage = if (duration > 0) {
                    (currentPosition.toFloat() / duration) * 100
                } else 0f

                try {
                    videoRepository.addToWatchHistory(
                        videoId = video.id,
                        watchDuration = watchDuration,
                        completionPercentage = completionPercentage,
                        profileId = null
                    )
                    Log.d(TAG, "Watch history saved on clear for: ${video.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save watch history on clear", e)
                }

                // Sync to YouTube for recommendations
                try {
                    youTubeHistorySync.reportWatchEvent(
                        videoId = video.id,
                        watchTimeSeconds = watchDuration,
                        totalDurationSeconds = totalDuration
                    )
                    Log.d(TAG, "YouTube history sync completed for ${video.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync YouTube history", e)
                }

                // End session
                if (currentSessionId != null) {
                    usageRepository.endSession(currentSessionId, 1)
                }

                // Save progress
                if (duration > 0) {
                    videoRepository.saveVideoProgress(video.id, currentPosition, duration)
                }
            }
        }

        player.release()
    }
}
