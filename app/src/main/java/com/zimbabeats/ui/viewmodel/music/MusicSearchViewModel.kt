package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PairingStatus
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

    // Cloud-based content filter (Firebase)
    private val contentFilter get() = cloudPairingClient.contentFilter

    private val _uiState = MutableStateFlow(MusicSearchUiState())
    val uiState: StateFlow<MusicSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    init {
        observeBridgeState()
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
     * Filter music search results using Cloud Content Filter (Firebase-based).
     * When not linked to family, all results are allowed (unrestricted mode).
     */
    private fun filterMusicResults(results: List<MusicSearchResult>): List<MusicSearchResult> {
        val filter = contentFilter ?: return results // Unrestricted mode if not linked

        return results.filter { result ->
            val (id, title, artistName, albumName, isExplicit) = when (result) {
                is MusicSearchResult.TrackResult -> listOf(
                    result.track.id,
                    result.track.title,
                    result.track.artistName,
                    result.track.albumName ?: "",
                    result.track.isExplicit.toString()
                )
                is MusicSearchResult.ArtistResult -> listOf(
                    result.artist.id,
                    result.artist.name,
                    result.artist.name,
                    "",
                    "false"
                )
                is MusicSearchResult.AlbumResult -> listOf(
                    result.album.id,
                    result.album.title,
                    result.album.artistName,
                    result.album.title,
                    "false"
                )
                is MusicSearchResult.PlaylistResult -> listOf(
                    result.playlist.id,
                    result.playlist.title,
                    result.playlist.author ?: "",
                    "",
                    "false"
                )
            }

            val blockResult = filter.shouldBlockMusicContent(
                trackId = id,
                title = title,
                artistId = "",
                artistName = artistName,
                albumName = albumName,
                genre = null,
                durationSeconds = 0L,
                isExplicit = isExplicit.toBoolean()
            )
            !blockResult.isBlocked
        }
    }

    /**
     * Check if a search query is allowed by parental controls (Firebase-based).
     */
    private fun isSearchAllowed(query: String): Boolean {
        val filter = contentFilter ?: return true // Unrestricted mode if not linked
        val blockResult = filter.shouldBlockSearch(query)
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
