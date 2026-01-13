package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PairingStatus
import com.zimbabeats.cloud.RemoteConfigManager
import com.zimbabeats.core.domain.model.music.MusicBrowseItem
import com.zimbabeats.core.domain.model.music.MusicBrowseSection
import com.zimbabeats.core.domain.model.music.MusicSearchFilter
import com.zimbabeats.core.domain.model.music.MusicSearchResult
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MusicHomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sections: List<MusicBrowseSection> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val error: String? = null,
    val parentalControlEnabled: Boolean = false
)

class MusicHomeViewModel(
    private val musicRepository: MusicRepository,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    // Cloud-based MUSIC filter (Firebase) - SEPARATE whitelist-only filter for music
    private val musicFilter get() = cloudPairingClient.musicFilter

    // Global content filter (RemoteConfig) - ALWAYS applies regardless of family linking
    private val remoteConfigManager = RemoteConfigManager()

    companion object {
        private const val TAG = "MusicHomeViewModel"

        // Curated search queries for KIDS MODE (parental control ON)
        private val KIDS_CURATED_SECTIONS = listOf(
            "Kids Songs" to "kids songs nursery rhymes",
            "Disney Music" to "disney songs for kids",
            "Fun & Dance" to "kids dance songs",
            "Lullabies" to "lullaby songs for children",
            "Learning Songs" to "abc learning songs kids"
        )

        // Curated search queries for GENERAL MODE (parental control OFF)
        private val GENERAL_CURATED_SECTIONS = listOf(
            "Top Hits" to "top hits 2024",
            "Pop Music" to "pop music",
            "Chill Vibes" to "chill lofi music",
            "Hip Hop" to "hip hop rap",
            "Rock Classics" to "rock classics",
            "R&B Soul" to "r&b soul music"
        )
    }

    private val _uiState = MutableStateFlow(MusicHomeUiState())
    val uiState: StateFlow<MusicHomeUiState> = _uiState.asStateFlow()

    // Store unfiltered content for re-filtering when settings change
    private var unfilteredSections: List<MusicBrowseSection> = emptyList()
    private var unfilteredRecentlyPlayed: List<Track> = emptyList()
    private var unfilteredMostPlayed: List<Track> = emptyList()

    /**
     * Filter tracks using both Global blocks (RemoteConfig) and Cloud Music Filter (Firebase).
     * Global blocks ALWAYS apply regardless of family linking.
     * Music whitelist only applies when linked to a family (ages 5-14 are whitelist-only).
     * Runs on IO dispatcher for performance.
     */
    private suspend fun filterTracksWithBridge(tracks: List<Track>): List<Track> = withContext(Dispatchers.Default) {
        tracks.filter { track ->
            // ALWAYS check global blocks first (regardless of family linking)
            val textToCheck = "${track.title} ${track.artistName} ${track.albumName ?: ""}"
            val globalKeywordBlock = remoteConfigManager.isGloballyBlocked(textToCheck)
            if (globalKeywordBlock.isBlocked) {
                Log.d(TAG, "Track '${track.title}' blocked by global keyword filter")
                return@filter false
            }

            val globalArtistBlock = remoteConfigManager.isArtistGloballyBlocked(
                track.artistId ?: "",
                track.artistName
            )
            if (globalArtistBlock.isBlocked) {
                Log.d(TAG, "Track '${track.title}' by '${track.artistName}' blocked by global artist filter")
                return@filter false
            }

            // If linked to family, apply MUSIC whitelist filter
            val filter = musicFilter
            if (filter != null) {
                // SECURITY: Block content until filter settings are loaded
                if (!filter.hasLoadedSettings()) {
                    Log.w(TAG, "Music filter settings not yet loaded - BLOCKING track until loaded: ${track.title}")
                    return@filter false
                }

                val blockResult = filter.shouldBlockMusic(
                    trackId = track.id,
                    title = track.title,
                    artistName = track.artistName,
                    albumName = track.albumName,
                    durationSeconds = track.duration / 1000L,
                    isExplicit = track.isExplicit
                )
                if (blockResult.isBlocked) {
                    Log.d(TAG, "Track '${track.title}' blocked by music filter: ${blockResult.reason}")
                    return@filter false
                }
            }

            true // Not blocked
        }
    }

    /**
     * Filter music browse items using both Global blocks and Cloud Music Filter.
     * Global blocks ALWAYS apply regardless of family linking.
     * Music whitelist only applies when linked to a family (ages 5-14 are whitelist-only).
     * Runs on Default dispatcher for performance.
     */
    private suspend fun filterBrowseItemsWithBridge(items: List<MusicBrowseItem>): List<MusicBrowseItem> = withContext(Dispatchers.Default) {
        items.filter { item ->
            // Extract item properties based on type
            val title: String
            val artistName: String
            val albumName: String
            val isExplicit: Boolean
            val durationSeconds: Long

            when (item) {
                is MusicBrowseItem.TrackItem -> {
                    title = item.track.title
                    artistName = item.track.artistName
                    albumName = item.track.albumName ?: ""
                    isExplicit = item.track.isExplicit
                    durationSeconds = item.track.duration / 1000L
                }
                is MusicBrowseItem.AlbumItem -> {
                    title = item.album.title
                    artistName = item.album.artistName
                    albumName = item.album.title
                    isExplicit = item.album.tracks.any { it.isExplicit }
                    durationSeconds = 0L
                }
                is MusicBrowseItem.ArtistItem -> {
                    title = item.artist.name
                    artistName = item.artist.name
                    albumName = ""
                    isExplicit = false
                    durationSeconds = 0L
                }
                is MusicBrowseItem.PlaylistItem -> {
                    title = item.playlist.title
                    artistName = item.playlist.author ?: ""
                    albumName = ""
                    isExplicit = false
                    durationSeconds = 0L
                }
            }

            // ALWAYS check global blocks first (regardless of family linking)
            val textToCheck = "$title $artistName $albumName"
            val globalKeywordBlock = remoteConfigManager.isGloballyBlocked(textToCheck)
            if (globalKeywordBlock.isBlocked) {
                Log.d(TAG, "Item '$title' blocked by global keyword filter")
                return@filter false
            }

            val globalArtistBlock = remoteConfigManager.isArtistGloballyBlocked("", artistName)
            if (globalArtistBlock.isBlocked) {
                Log.d(TAG, "Item '$title' by '$artistName' blocked by global artist filter")
                return@filter false
            }

            // If linked to family, apply MUSIC whitelist filter
            val filter = musicFilter
            if (filter != null) {
                // SECURITY: Block content until filter settings are loaded
                if (!filter.hasLoadedSettings()) {
                    Log.w(TAG, "Music filter settings not yet loaded - BLOCKING item until loaded: $title")
                    return@filter false
                }

                val blockResult = filter.shouldBlockMusic(
                    trackId = "",
                    title = title,
                    artistName = artistName,
                    albumName = albumName,
                    durationSeconds = durationSeconds,
                    isExplicit = isExplicit
                )
                if (blockResult.isBlocked) {
                    Log.d(TAG, "Item '$title' blocked by music filter: ${blockResult.reason}")
                    return@filter false
                }
            }

            true // Not blocked
        }
    }

    init {
        observeBridgeState()
        observeFilterSettings()
        // Note: loadRecentlyPlayed() and loadMostPlayed() are now called from observeBridgeState()
        // after pairing status is known, ensuring proper filtering
        startAutoRefresh()
    }

    /**
     * Observe music filter settings changes from Firebase (whitelist updates)
     * Re-filters all music content when parent changes settings
     */
    private fun observeFilterSettings() {
        viewModelScope.launch {
            musicFilter?.musicSettings?.collect { settings ->
                Log.d(TAG, "Filter settings changed - re-filtering music home content")

                // Re-filter sections
                if (unfilteredSections.isNotEmpty()) {
                    val filteredSections = unfilteredSections.map { section ->
                        val filteredItems = filterBrowseItemsWithBridge(section.items)
                        section.copy(items = filteredItems)
                    }.filter { it.items.isNotEmpty() }
                    Log.d(TAG, "After re-filter: ${filteredSections.size} sections visible")
                    _uiState.value = _uiState.value.copy(sections = filteredSections)
                }

                // Re-filter recently played
                if (unfilteredRecentlyPlayed.isNotEmpty()) {
                    val filteredRecent = filterTracksWithBridge(unfilteredRecentlyPlayed)
                    Log.d(TAG, "After re-filter: ${filteredRecent.size} recently played visible")
                    _uiState.value = _uiState.value.copy(recentlyPlayed = filteredRecent)
                }

                // Re-filter most played
                if (unfilteredMostPlayed.isNotEmpty()) {
                    val filteredMost = filterTracksWithBridge(unfilteredMostPlayed)
                    Log.d(TAG, "After re-filter: ${filteredMost.size} most played visible")
                    _uiState.value = _uiState.value.copy(mostPlayed = filteredMost)
                }
            }
        }
    }

    /**
     * Auto-refresh content every 1 minute
     */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L) // 1 minute
                Log.d(TAG, "Auto-refreshing music content...")
                loadMusicHomeInternal()
            }
        }
    }

    /**
     * Observe cloud pairing state for changes from Firebase
     */
    private fun observeBridgeState() {
        viewModelScope.launch {
            cloudPairingClient.pairingStatus.collect { pairingStatus ->
                val isLinkedToFamily = pairingStatus is PairingStatus.Paired
                val isKidsMode = isLinkedToFamily // Kids mode ON when linked to family

                Log.d(TAG, "Cloud state received - linkedToFamily: $isLinkedToFamily")

                val previousEnabled = _uiState.value.parentalControlEnabled
                val settingsChanged = previousEnabled != isKidsMode

                _uiState.value = _uiState.value.copy(
                    parentalControlEnabled = isKidsMode
                )

                // If linked to family, wait for music filter settings to load before showing content
                if (isLinkedToFamily) {
                    val filter = musicFilter
                    if (filter != null && !filter.hasLoadedSettings()) {
                        Log.d(TAG, "Waiting for music filter settings to load before showing content...")
                        // Wait up to 5 seconds for settings to load
                        var waitTime = 0
                        while (!filter.hasLoadedSettings() && waitTime < 5000) {
                            kotlinx.coroutines.delay(100)
                            waitTime += 100
                        }
                        if (filter.hasLoadedSettings()) {
                            Log.d(TAG, "Music filter settings loaded after ${waitTime}ms")
                        } else {
                            Log.w(TAG, "Music filter settings not loaded after 5 seconds, proceeding with blocking mode")
                        }
                    }
                }

                // Refresh content if parental control state changed
                if (settingsChanged) {
                    Log.d(TAG, "Parental settings CHANGED, refreshing all content")
                    _uiState.value = _uiState.value.copy(
                        sections = emptyList(),
                        recentlyPlayed = emptyList(),
                        mostPlayed = emptyList(),
                        isLoading = true
                    )
                    loadMusicHome()
                    loadRecentlyPlayed()
                    loadMostPlayed()
                } else if (_uiState.value.sections.isEmpty()) {
                    // Initial load
                    Log.d(TAG, "Initial load, loading music content...")
                    loadMusicHome()
                    loadRecentlyPlayed()
                    loadMostPlayed()
                }
            }
        }
    }

    fun loadMusicHome() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = musicRepository.getMusicHome()) {
                is Resource.Success -> {
                    if (result.data.isNotEmpty()) {
                        // Store unfiltered sections for re-filtering when settings change
                        unfilteredSections = result.data

                        // Apply filtering
                        val filteredSections = result.data.map { section ->
                            val filteredItems = filterBrowseItemsWithBridge(section.items)
                            section.copy(items = filteredItems)
                        }.filter { it.items.isNotEmpty() }

                        Log.d(TAG, "Loaded ${filteredSections.size} sections from browse API (from ${result.data.size} raw)")

                        // If all sections filtered out (whitelist mode with no matching content), load curated kids content
                        if (filteredSections.isEmpty() && _uiState.value.parentalControlEnabled) {
                            Log.d(TAG, "All browse content filtered out by whitelist - loading kids curated content")
                            loadCuratedContent()
                        } else {
                            _uiState.value = _uiState.value.copy(
                                sections = filteredSections,
                                isLoading = false
                            )
                        }
                    } else {
                        // Browse API returned empty - load fallback curated content
                        Log.d(TAG, "Browse API returned empty, loading curated content")
                        loadCuratedContent()
                    }
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load music home: ${result.message}, trying curated content")
                    // On error, also try curated content as fallback
                    loadCuratedContent()
                }
                else -> {}
            }
        }
    }

    private suspend fun loadCuratedContent() {
        val isKidsMode = _uiState.value.parentalControlEnabled
        val curatedSections = if (isKidsMode) KIDS_CURATED_SECTIONS else GENERAL_CURATED_SECTIONS
        Log.d(TAG, "Loading curated music sections (kids mode: $isKidsMode)...")

        // Build unfiltered sections first
        val rawSections = curatedSections.mapNotNull { (title, query) ->
            try {
                val result = musicRepository.searchMusic(query, MusicSearchFilter.SONGS)
                if (result is Resource.Success && result.data.isNotEmpty()) {
                    val items = result.data.take(10).mapNotNull { searchResult ->
                        when (searchResult) {
                            is MusicSearchResult.TrackResult -> MusicBrowseItem.TrackItem(searchResult.track)
                            is MusicSearchResult.AlbumResult -> MusicBrowseItem.AlbumItem(searchResult.album)
                            is MusicSearchResult.ArtistResult -> MusicBrowseItem.ArtistItem(searchResult.artist)
                            is MusicSearchResult.PlaylistResult -> MusicBrowseItem.PlaylistItem(searchResult.playlist)
                        }
                    }
                    if (items.isNotEmpty()) MusicBrowseSection(title = title, items = items) else null
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load curated section '$title': ${e.message}")
                null
            }
        }

        // Store unfiltered sections for re-filtering when settings change
        unfilteredSections = rawSections

        // Apply filtering
        val filteredSections = rawSections.map { section ->
            val filteredItems = filterBrowseItemsWithBridge(section.items)
            section.copy(items = filteredItems)
        }.filter { it.items.isNotEmpty() }

        Log.d(TAG, "Loaded ${filteredSections.size} curated sections (from ${rawSections.size} raw)")
        _uiState.value = _uiState.value.copy(
            sections = filteredSections,
            isLoading = false,
            error = if (filteredSections.isEmpty()) "No music content available" else null
        )
    }

    private fun loadRecentlyPlayed() {
        viewModelScope.launch {
            musicRepository.getRecentlyPlayed(10).collect { tracks ->
                unfilteredRecentlyPlayed = tracks // Store for re-filtering when settings change
                val filteredTracks = filterTracksWithBridge(tracks)
                Log.d(TAG, "Loaded ${tracks.size} recently played, ${filteredTracks.size} after filtering")
                _uiState.value = _uiState.value.copy(recentlyPlayed = filteredTracks)
            }
        }
    }

    private fun loadMostPlayed() {
        viewModelScope.launch {
            musicRepository.getMostPlayed(10).collect { tracks ->
                unfilteredMostPlayed = tracks // Store for re-filtering when settings change
                val filteredTracks = filterTracksWithBridge(tracks)
                Log.d(TAG, "Loaded ${tracks.size} most played, ${filteredTracks.size} after filtering")
                _uiState.value = _uiState.value.copy(mostPlayed = filteredTracks)
            }
        }
    }

    /**
     * Manual refresh - pull to refresh
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            loadMusicHomeInternal()
            loadRecentlyPlayed()
            loadMostPlayed()
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    private suspend fun loadMusicHomeInternal() {
        when (val result = musicRepository.getMusicHome()) {
            is Resource.Success -> {
                if (result.data.isNotEmpty()) {
                    // Store unfiltered sections for re-filtering when settings change
                    unfilteredSections = result.data

                    // Apply filtering
                    val filteredSections = result.data.map { section ->
                        val filteredItems = filterBrowseItemsWithBridge(section.items)
                        section.copy(items = filteredItems)
                    }.filter { it.items.isNotEmpty() }

                    Log.d(TAG, "Loaded ${filteredSections.size} sections from browse API (from ${result.data.size} raw)")
                    _uiState.value = _uiState.value.copy(
                        sections = filteredSections,
                        isLoading = false
                    )
                } else {
                    loadCuratedContent()
                }
            }
            is Resource.Error -> {
                Log.e(TAG, "Failed to load music home: ${result.message}, trying curated content")
                loadCuratedContent()
            }
            else -> {}
        }
    }
}
