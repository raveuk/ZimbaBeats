package com.zimbabeats.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PairingStatus
import com.zimbabeats.core.domain.model.SearchSuggestion
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.repository.SearchRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<Video> = emptyList(),
    val suggestions: List<SearchSuggestion> = emptyList(),
    val youtubeSuggestions: List<String> = emptyList(), // Autocomplete from YouTube
    val recentSearches: List<String> = emptyList(),
    val error: String? = null,
    val hasSearched: Boolean = false,
    val searchBlocked: Boolean = false,
    val searchBlockedMessage: String? = null,
    val kidSafeModeEnabled: Boolean = false,
    val correctedQuery: String? = null // "Did you mean" spelling correction
)

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    // Cloud-based content filter (Firebase)
    private val contentFilter get() = cloudPairingClient.contentFilter

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    init {
        observeBridgeState()
        loadRecentSearches()
        loadSuggestions()
    }

    private fun observeBridgeState() {
        viewModelScope.launch {
            cloudPairingClient.pairingStatus.collect { pairingStatus ->
                val isLinkedToFamily = pairingStatus is PairingStatus.Paired
                val isKidSafeMode = isLinkedToFamily

                // Get the age rating from cloud settings
                val ageRatingString = cloudPairingClient.cloudSettings.value?.ageRating

                // Use safety mode for young children (5-8)
                // For ages 13+, use regular YouTube (WEB) with CloudContentFilter
                val useYouTubeKidsApi = isKidSafeMode && ageRatingString in listOf(
                    "FIVE_PLUS", "EIGHT_PLUS"
                )

                android.util.Log.d("SearchViewModel", "Kid safe mode: isLinkedToFamily=$isLinkedToFamily, ageRating=$ageRatingString, useYouTubeKidsApi=$useYouTubeKidsApi")
                searchRepository.setKidSafeMode(useYouTubeKidsApi)

                // Re-filter existing results when settings change
                val currentResults = _uiState.value.searchResults
                _uiState.value = _uiState.value.copy(
                    kidSafeModeEnabled = useYouTubeKidsApi,
                    searchResults = if (currentResults.isNotEmpty()) filterVideosByCloud(currentResults) else currentResults
                )
            }
        }
    }

    /**
     * Filter videos using Cloud Content Filter (Firebase-based).
     * When not linked to family, all videos are allowed (unrestricted mode).
     */
    private fun filterVideosByCloud(videos: List<Video>): List<Video> {
        // CRITICAL FIX: If not paired with parent app, skip cloud filtering entirely
        // This fixes the bug where unpaired devices were getting filtered
        if (!cloudPairingClient.isPaired()) {
            android.util.Log.d("SearchViewModel", "Device not paired - returning all ${videos.size} videos unfiltered")
            return videos
        }

        val filter = contentFilter

        // Null safety: If filter is null, allow all videos (unrestricted mode)
        if (filter == null) {
            android.util.Log.w("SearchViewModel", "Content filter is null - allowing all ${videos.size} videos")
            return videos
        }

        // If filter settings haven't loaded from Firestore yet, don't block anything
        if (!filter.hasLoadedSettings()) {
            android.util.Log.w("SearchViewModel", "Filter settings not yet loaded - allowing all ${videos.size} videos")
            return videos
        }

        return videos.filter { video ->
            // Convert duration to seconds if stored in milliseconds
            val durationSeconds = if (video.duration > 100000) video.duration / 1000 else video.duration
            val blockResult = filter.shouldBlockContent(
                videoId = video.id,
                title = video.title,
                channelId = video.channelId,
                channelName = video.channelName,
                description = video.description,
                durationSeconds = durationSeconds,
                isLiveStream = false, // Video model doesn't track live streams
                category = video.category?.name
            )
            if (blockResult.isBlocked) {
                android.util.Log.d("SearchViewModel", "Filtered: ${video.title} - reason: ${blockResult.reason}")
            }
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

    /**
     * Calculate relevance score for a video based on how well it matches the search query.
     * Higher score = more relevant to the search.
     */
    private fun calculateRelevanceScore(video: Video, query: String): Int {
        val queryLower = query.lowercase().trim()
        val titleLower = video.title.lowercase()
        val channelLower = video.channelName.lowercase()
        var score = 0

        // Exact query match in title (highest relevance)
        if (titleLower.contains(queryLower)) {
            score += 100
        }

        // Exact query match in channel name
        if (channelLower.contains(queryLower)) {
            score += 30
        }

        // Word-by-word matching for multi-word queries
        val queryWords = queryLower.split(" ").filter { it.length >= 2 }
        queryWords.forEach { word ->
            if (titleLower.contains(word)) {
                score += 25
            }
            if (channelLower.contains(word)) {
                score += 10
            }
        }

        return score
    }

    /**
     * Sort videos by relevance to the search query.
     * Most relevant videos appear first, unrelated videos appear at the bottom.
     */
    private fun sortByRelevance(videos: List<Video>, query: String): List<Video> {
        return videos.sortedByDescending { calculateRelevanceScore(it, query) }
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            searchRepository.getRecentSearchQueries(10).collect { queries ->
                _uiState.value = _uiState.value.copy(recentSearches = queries)
            }
        }
    }

    private fun loadSuggestions() {
        viewModelScope.launch {
            searchRepository.getTopSuggestions(10).collect { suggestions ->
                _uiState.value = _uiState.value.copy(suggestions = suggestions)
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query, correctedQuery = null)

        // Cancel previous jobs
        searchJob?.cancel()
        suggestionJob?.cancel()

        if (query.isNotBlank()) {
            // Debounce local search
            searchJob = viewModelScope.launch {
                delay(300)
                searchLocally(query)
            }

            // Fetch YouTube autocomplete suggestions (helps with typos)
            suggestionJob = viewModelScope.launch {
                delay(200) // Faster debounce for suggestions
                fetchYouTubeSuggestions(query)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                hasSearched = false,
                youtubeSuggestions = emptyList()
            )
        }
    }

    private suspend fun fetchYouTubeSuggestions(query: String) {
        if (query.length < 2) return // Don't fetch for very short queries

        try {
            when (val result = searchRepository.getYouTubeSuggestions(query)) {
                is Resource.Success -> {
                    // Filter suggestions based on parental controls from companion app
                    val filteredSuggestions = result.data
                        .filter { suggestion ->
                            // Check if suggestion is allowed by parental controls
                            isSearchAllowed(suggestion)
                        }
                        .distinct() // Remove duplicates
                        .take(8)

                    _uiState.value = _uiState.value.copy(
                        youtubeSuggestions = filteredSuggestions
                    )
                }
                is Resource.Error -> {
                    // Silently fail - suggestions are not critical
                }
                else -> {}
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Ignore cancellation - user typed something new
        } catch (e: Exception) {
            // Silently fail - suggestions are not critical
        }
    }

    fun selectSuggestion(suggestion: String) {
        _uiState.value = _uiState.value.copy(
            query = suggestion,
            youtubeSuggestions = emptyList()
        )
        performSearch()
    }

    fun searchWithCorrection() {
        val correctedQuery = _uiState.value.correctedQuery ?: return
        _uiState.value = _uiState.value.copy(
            query = correctedQuery,
            correctedQuery = null
        )
        performSearch()
    }

    private fun searchLocally(query: String) {
        viewModelScope.launch {
            // Don't show loading for local search - it should be instant
            searchRepository.searchVideosLocally(query).collect { results ->
                // Only show local results as suggestions, don't set hasSearched
                // hasSearched should only be true after explicit YouTube API search
                val filtered = filterVideosByCloud(results)
                val sorted = sortByRelevance(filtered, query)
                _uiState.value = _uiState.value.copy(
                    searchResults = sorted
                    // Note: NOT setting hasSearched = true here
                    // This prevents "No results found" from showing before API search
                )
            }
        }
    }

    fun performSearch() {
        val query = _uiState.value.query
        if (query.isBlank()) return

        // Cancel any pending jobs
        searchJob?.cancel()
        suggestionJob?.cancel()

        // Check if search is allowed based on parental controls from companion app
        if (!isSearchAllowed(query)) {
            _uiState.value = _uiState.value.copy(
                searchBlocked = true,
                searchBlockedMessage = "This search is not allowed with current parental controls",
                searchResults = emptyList(),
                hasSearched = true
            )
            return
        }

        viewModelScope.launch {
            // Clear previous results and show loading state
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                hasSearched = false,
                error = null,
                searchBlocked = false,
                searchBlockedMessage = null,
                youtubeSuggestions = emptyList() // Clear suggestions when searching
            )

            // Update suggestion
            searchRepository.updateSuggestion(query)

            // Search YouTube - filtering is done after results are returned
            // Using maxResults = 100 for better coverage, especially for channel/creator searches
            when (val result = searchRepository.searchVideos(query, maxResults = 100)) {
                is Resource.Success -> {
                    val searchResult = result.data
                    // First apply safety filtering via Bridge, then sort by relevance to the search query
                    val filteredVideos = filterVideosByCloud(searchResult.videos)
                    val sortedVideos = sortByRelevance(filteredVideos, query)
                    _uiState.value = _uiState.value.copy(
                        searchResults = sortedVideos,
                        isSearching = false,
                        hasSearched = true,
                        correctedQuery = searchResult.correctedQuery // "Did you mean" suggestion
                    )

                    // Save to history with result count
                    searchRepository.saveSearchHistory(query, filteredVideos.size)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        hasSearched = true,
                        error = result.message
                    )
                }
                is Resource.Loading -> {
                    // Already handled by isSearching state
                }
            }
        }
    }

    fun clearSearch() {
        _uiState.value = SearchUiState()
        loadRecentSearches()
        loadSuggestions()
    }

    fun deleteSearchHistory(query: String) {
        viewModelScope.launch {
            searchRepository.deleteSearchHistory(query)
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            searchRepository.clearSearchHistory()
        }
    }
}
