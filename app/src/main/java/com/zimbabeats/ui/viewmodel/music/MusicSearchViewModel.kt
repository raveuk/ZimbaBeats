package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PairingStatus
import com.zimbabeats.cloud.RemoteConfigManager
import com.zimbabeats.core.domain.model.music.MusicSearchFilter
import com.zimbabeats.core.domain.model.music.MusicSearchResult
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MusicSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<MusicSearchResult> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val filter: MusicSearchFilter = MusicSearchFilter.SONGS,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val searchBlocked: Boolean = false,
    val searchBlockedMessage: String? = null,
    val kidSafeModeEnabled: Boolean = false
)

class MusicSearchViewModel(
    private val musicRepository: MusicRepository,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    companion object {
        private const val TAG = "MusicSearchViewModel"
    }

    // Cloud-based MUSIC filter (Firebase) - SEPARATE whitelist-only filter for music
    private val musicFilter get() = cloudPairingClient.musicFilter

    // Global content filter (RemoteConfig) - ALWAYS applies regardless of family linking
    private val remoteConfigManager = RemoteConfigManager()

    private val _uiState = MutableStateFlow(MusicSearchUiState())
    val uiState: StateFlow<MusicSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    // Store unfiltered results for re-filtering when settings change
    private var unfilteredResults: List<MusicSearchResult> = emptyList()

    init {
        observeBridgeState()
        observeFilterSettings()
    }

    /**
     * Observe music filter settings changes from Firebase (whitelist updates)
     * Re-filters search results when parent changes settings
     */
    private fun observeFilterSettings() {
        viewModelScope.launch {
            // BUG-001 FIX: Add null safety - musicFilter may be null when not linked to family
            val filter = musicFilter
            if (filter == null) {
                Log.d(TAG, "Not linked to family - music filter not active, skipping settings observation")
                return@launch
            }

            filter.musicSettings.collect { settings ->
                Log.d(TAG, "Music filter settings changed - re-filtering ${unfilteredResults.size} results")
                if (unfilteredResults.isNotEmpty()) {
                    val filteredResults = filterMusicResults(unfilteredResults)
                    Log.d(TAG, "After re-filter: ${filteredResults.size} results visible")
                    _uiState.value = _uiState.value.copy(searchResults = filteredResults)
                }
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
                val isKidSafeMode = isLinkedToFamily // Kids mode ON when linked to family

                // Re-filter existing results when settings change
                val currentResults = _uiState.value.searchResults
                _uiState.value = _uiState.value.copy(
                    kidSafeModeEnabled = isKidSafeMode,
                    searchResults = if (currentResults.isNotEmpty()) filterMusicResults(currentResults) else currentResults
                )
            }
        }
    }

    /**
     * Filter music search results using both Global blocks and Cloud Content Filter.
     * Global blocks ALWAYS apply regardless of family linking.
     */
    private fun filterMusicResults(results: List<MusicSearchResult>): List<MusicSearchResult> {
        return results.filter { result ->
            // Extract result properties based on type
            val id: String
            val title: String
            val artistId: String
            val artistName: String
            val albumName: String
            val isExplicit: Boolean

            val durationSeconds: Long

            when (result) {
                is MusicSearchResult.TrackResult -> {
                    id = result.track.id
                    title = result.track.title
                    artistId = result.track.artistId ?: ""
                    artistName = result.track.artistName
                    albumName = result.track.albumName ?: ""
                    isExplicit = result.track.isExplicit
                    durationSeconds = result.track.duration / 1000L // Convert ms to seconds
                }
                is MusicSearchResult.ArtistResult -> {
                    id = result.artist.id
                    title = result.artist.name
                    artistId = result.artist.id
                    artistName = result.artist.name
                    albumName = ""
                    isExplicit = false
                    durationSeconds = 0L
                }
                is MusicSearchResult.AlbumResult -> {
                    id = result.album.id
                    title = result.album.title
                    artistId = ""
                    artistName = result.album.artistName
                    albumName = result.album.title
                    // Check if any track in the album is explicit
                    isExplicit = result.album.tracks.any { it.isExplicit }
                    durationSeconds = 0L // Albums are containers
                }
                is MusicSearchResult.PlaylistResult -> {
                    id = result.playlist.id
                    title = result.playlist.title
                    artistId = ""
                    artistName = result.playlist.author ?: ""
                    albumName = ""
                    isExplicit = false
                    durationSeconds = 0L
                }
            }

            // ALWAYS check global blocks first (regardless of family linking)
            val textToCheck = "$title $artistName $albumName"
            val globalKeywordBlock = remoteConfigManager.isGloballyBlocked(textToCheck)
            if (globalKeywordBlock.isBlocked) {
                Log.d(TAG, "Search result '$title' blocked by global keyword filter")
                return@filter false
            }

            val globalArtistBlock = remoteConfigManager.isArtistGloballyBlocked(artistId, artistName)
            if (globalArtistBlock.isBlocked) {
                Log.d(TAG, "Search result '$title' by '$artistName' blocked by global artist filter")
                return@filter false
            }

            // If linked to family, apply MUSIC whitelist filter
            // BUG-002 FIX: Add null safety - musicFilter may be null when not linked to family
            val filter = musicFilter
            if (filter == null) {
                // Not linked to family - allow all music (only global blocks applied above)
                return@filter true
            }

            // SECURITY: Block content until filter settings are loaded
            if (!filter.hasLoadedSettings()) {
                Log.w(TAG, "Music filter settings not yet loaded - BLOCKING search result until loaded: $title")
                return@filter false
            }

            val blockResult = filter.shouldBlockMusic(
                trackId = id,
                title = title,
                artistName = artistName,
                albumName = albumName,
                durationSeconds = durationSeconds,
                isExplicit = isExplicit
            )
            if (blockResult.isBlocked) {
                Log.d(TAG, "Search result '$title' blocked by music filter: ${blockResult.reason}")
                return@filter false
            }

            true // Not blocked
        }
    }

    /**
     * Check if a search query is allowed by music filter (Firebase-based whitelist).
     */
    private fun isSearchAllowed(query: String): Boolean {
        // BUG-003 FIX: Add null safety - musicFilter may be null when not linked to family
        val filter = musicFilter
        if (filter == null) {
            // Not linked to family - allow all searches (only global blocks apply)
            Log.d(TAG, "Not linked to family - allowing search query: $query")
            return true
        }

        // SECURITY: Block search until filter settings are loaded
        if (!filter.hasLoadedSettings()) {
            Log.w(TAG, "Music filter settings not yet loaded - BLOCKING search query until loaded: $query")
            return false
        }
        val blockResult = filter.isSearchAllowed(query)
        return !blockResult.isBlocked
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)

        // Debounce suggestions
        suggestionJob?.cancel()
        if (query.length >= 2) {
            suggestionJob = viewModelScope.launch {
                delay(200)
                loadSuggestions(query)
            }
        } else {
            _uiState.value = _uiState.value.copy(suggestions = emptyList())
        }
    }

    private suspend fun loadSuggestions(query: String) {
        when (val result = musicRepository.getSearchSuggestions(query)) {
            is Resource.Success -> {
                // Filter suggestions based on parental controls
                val filteredSuggestions = result.data
                    .filter { suggestion -> isSearchAllowed(suggestion) }
                    .distinct()
                    .take(8)

                _uiState.value = _uiState.value.copy(suggestions = filteredSuggestions)
            }
            is Resource.Error -> {
                Log.e(TAG, "Failed to get suggestions: ${result.message}")
            }
            else -> {}
        }
    }

    fun performSearch() {
        val query = _uiState.value.query
        if (query.isBlank()) return

        searchJob?.cancel()
        suggestionJob?.cancel()

        // Check if search is allowed based on parental controls
        if (!isSearchAllowed(query)) {
            _uiState.value = _uiState.value.copy(
                searchBlocked = true,
                searchBlockedMessage = "This search is not allowed with current parental controls",
                searchResults = emptyList(),
                hasSearched = true,
                isSearching = false,
                suggestions = emptyList()
            )
            Log.d(TAG, "Search blocked by parental controls: $query")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                hasSearched = false,
                error = null,
                searchBlocked = false,
                searchBlockedMessage = null,
                suggestions = emptyList()
            )

            when (val result = musicRepository.searchMusic(query, _uiState.value.filter)) {
                is Resource.Success -> {
                    unfilteredResults = result.data // Store for re-filtering when settings change
                    // Filter results using Bridge
                    val filteredResults = filterMusicResults(result.data)

                    val trackCount = filteredResults.count { it is MusicSearchResult.TrackResult }
                    val artistCount = filteredResults.count { it is MusicSearchResult.ArtistResult }
                    val albumCount = filteredResults.count { it is MusicSearchResult.AlbumResult }
                    Log.d(TAG, "Search returned ${result.data.size} results, filtered to ${filteredResults.size}: tracks=$trackCount, artists=$artistCount, albums=$albumCount")
                    filteredResults.take(3).forEach { r ->
                        when (r) {
                            is MusicSearchResult.TrackResult -> Log.d(TAG, "  Track: ${r.track.title} (${r.track.id})")
                            is MusicSearchResult.ArtistResult -> Log.d(TAG, "  Artist: ${r.artist.name} (${r.artist.id})")
                            is MusicSearchResult.AlbumResult -> Log.d(TAG, "  Album: ${r.album.title} (${r.album.id})")
                            is MusicSearchResult.PlaylistResult -> Log.d(TAG, "  Playlist: ${r.playlist.title}")
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        searchResults = filteredResults,
                        isSearching = false,
                        hasSearched = true
                    )
                }
                is Resource.Error -> {
                    Log.e(TAG, "Search failed: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        hasSearched = true,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun setFilter(filter: MusicSearchFilter) {
        if (_uiState.value.filter != filter) {
            _uiState.value = _uiState.value.copy(filter = filter)
            // Re-search with new filter if we have a query
            if (_uiState.value.query.isNotBlank() && _uiState.value.hasSearched) {
                performSearch()
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        suggestionJob?.cancel()
        _uiState.value = MusicSearchUiState()
    }

    fun selectSuggestion(suggestion: String) {
        _uiState.value = _uiState.value.copy(query = suggestion, suggestions = emptyList())
        performSearch()
    }
}
