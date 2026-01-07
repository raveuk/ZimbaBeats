package com.zimbabeats.ui.screen

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.MusicSearchFilter
import com.zimbabeats.core.domain.model.music.MusicSearchResult
import com.zimbabeats.ui.data.KidSafeSuggestions
import com.zimbabeats.ui.theme.*
import com.zimbabeats.ui.viewmodel.SearchViewModel
import com.zimbabeats.ui.viewmodel.music.MusicSearchViewModel
import com.zimbabeats.media.music.MusicPlaybackManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

enum class SearchMode {
    VIDEO, MUSIC
}

/**
 * UI states for search screen
 */
enum class SearchUIState {
    EMPTY,              // No query, show popular suggestions
    SUGGESTIONS,        // User is typing, show filtered suggestions
    LOADING,            // Search in progress
    RESULTS,            // Search completed, show results
    BLOCKED             // Search was blocked by parental controls
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onTrackClick: (String) -> Unit = {},
    onPlayTracks: (List<com.zimbabeats.core.domain.model.music.Track>, Int) -> Unit = { _, _ -> },
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    initialSearchMode: SearchMode = SearchMode.VIDEO,
    videoViewModel: SearchViewModel = koinViewModel(),
    musicViewModel: MusicSearchViewModel = koinViewModel(),
    musicPlaybackManager: MusicPlaybackManager = koinInject()
) {
    var searchMode by rememberSaveable { mutableStateOf(initialSearchMode) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val videoUiState by videoViewModel.uiState.collectAsState()
    val musicUiState by musicViewModel.uiState.collectAsState()

    // Get current playing track for "now playing" indicator
    val musicPlaybackState by musicPlaybackManager.playbackState.collectAsState()
    val currentPlayingTrackId = musicPlaybackState.currentTrack?.id

    // Determine which state to show
    val query = if (searchMode == SearchMode.VIDEO) videoUiState.query else musicUiState.query
    val isSearching = if (searchMode == SearchMode.VIDEO) videoUiState.isSearching else musicUiState.isSearching
    val hasSearched = if (searchMode == SearchMode.VIDEO) videoUiState.hasSearched else musicUiState.hasSearched
    val searchBlocked = if (searchMode == SearchMode.VIDEO) videoUiState.searchBlocked else musicUiState.searchBlocked

    // Compute filtered suggestions directly (no memoization issues)
    val filteredSuggestions = KidSafeSuggestions.filterSuggestions(
        query = query,
        isMusic = searchMode == SearchMode.MUSIC
    )

    // Log for debugging
    Log.d("SearchScreen", "Query: '$query', Mode: $searchMode, Suggestions: ${filteredSuggestions.size}")

    // Determine current UI state directly (no LaunchedEffect delay)
    val currentUIState = when {
        searchBlocked -> SearchUIState.BLOCKED
        isSearching -> SearchUIState.LOADING
        hasSearched -> SearchUIState.RESULTS
        else -> SearchUIState.SUGGESTIONS // Always show suggestions when not searched
    }

    // Auto-focus search field and show keyboard when screen opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Spotify-style search bar
                    TextField(
                        value = query,
                        onValueChange = {
                            if (searchMode == SearchMode.VIDEO) {
                                videoViewModel.onQueryChange(it)
                            } else {
                                musicViewModel.onQueryChange(it)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = {
                            Text(
                                if (searchMode == SearchMode.VIDEO) "What do you want to watch?"
                                else "What do you want to listen to?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchMode == SearchMode.VIDEO) {
                                    videoViewModel.performSearch()
                                } else {
                                    musicViewModel.performSearch()
                                }
                            }
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = {
                                    if (searchMode == SearchMode.VIDEO) {
                                        videoViewModel.clearSearch()
                                    } else {
                                        musicViewModel.clearSearch()
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        "Clear",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                if (searchMode == SearchMode.VIDEO) Icons.Default.Search
                                else Icons.Default.MusicNote,
                                "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Video/Music mode toggle - always visible at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = searchMode == SearchMode.VIDEO,
                    onClick = { searchMode = SearchMode.VIDEO },
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
                    selected = searchMode == SearchMode.MUSIC,
                    onClick = { searchMode = SearchMode.MUSIC },
                    label = { Text("Music") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                // Music filters (only show when in music mode and has results)
                if (searchMode == SearchMode.MUSIC && musicUiState.hasSearched) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = musicUiState.filter == MusicSearchFilter.SONGS,
                        onClick = { musicViewModel.setFilter(MusicSearchFilter.SONGS) },
                        label = { Text("Songs") }
                    )
                    FilterChip(
                        selected = musicUiState.filter == MusicSearchFilter.ALBUMS,
                        onClick = { musicViewModel.setFilter(MusicSearchFilter.ALBUMS) },
                        label = { Text("Albums") }
                    )
                    FilterChip(
                        selected = musicUiState.filter == MusicSearchFilter.ARTISTS,
                        onClick = { musicViewModel.setFilter(MusicSearchFilter.ARTISTS) },
                        label = { Text("Artists") }
                    )
                }
            }

            // Kid-safe mode indicator (for both video and music modes)
            val kidSafeModeEnabled = if (searchMode == SearchMode.VIDEO) videoUiState.kidSafeModeEnabled else musicUiState.kidSafeModeEnabled
            if (kidSafeModeEnabled) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Kid Safe Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Kid-Safe Mode Active - Content is filtered for children",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Main content with Crossfade for smooth transitions
            Crossfade(
                targetState = Triple(currentUIState, query, searchMode),
                label = "search_content"
            ) { (uiState, _, _) ->
                when (uiState) {
                    SearchUIState.EMPTY, SearchUIState.SUGGESTIONS -> {
                        // Show suggestions (both empty and typing states)
                        val currentQuery = if (searchMode == SearchMode.VIDEO) videoUiState.query else musicUiState.query

                        // Get live YouTube suggestions from ViewModel
                        val liveSuggestions = if (searchMode == SearchMode.VIDEO) {
                            videoUiState.youtubeSuggestions
                        } else {
                            musicUiState.suggestions
                        }

                        // Get curated suggestions as fallback
                        val curatedSuggestions = KidSafeSuggestions.filterSuggestions(
                            query = currentQuery,
                            isMusic = searchMode == SearchMode.MUSIC
                        )

                        // Popular suggestions when empty
                        val popularSuggestions = KidSafeSuggestions.filterSuggestions(
                            query = "",
                            isMusic = searchMode == SearchMode.MUSIC
                        )

                        // Use live suggestions if available, otherwise curated
                        val suggestions = when {
                            liveSuggestions.isNotEmpty() -> liveSuggestions
                            curatedSuggestions.isNotEmpty() -> curatedSuggestions
                            currentQuery.isNotBlank() -> popularSuggestions
                            else -> popularSuggestions
                        }

                        val hasLiveSuggestions = liveSuggestions.isNotEmpty()
                        val noMatchesFound = suggestions.isEmpty() && currentQuery.isNotBlank()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Show current query status
                            if (currentQuery.isNotBlank()) {
                                item {
                                    Text(
                                        text = "Searching: \"$currentQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }

                            // No matches message - only show when no live suggestions either
                            if (noMatchesFound && !hasLiveSuggestions) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "No suggestions for \"$currentQuery\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Press Enter to search, or try one of the popular searches below:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Title
                            item {
                                Text(
                                    text = if (currentQuery.isBlank()) {
                                        if (searchMode == SearchMode.VIDEO) "Popular Searches" else "Popular Music"
                                    } else if (noMatchesFound) {
                                        "Try these popular searches"
                                    } else {
                                        "Suggestions"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            // Suggestion chips - different styling for live vs curated
                            item {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    suggestions.forEach { suggestion ->
                                        SuggestionChip(
                                            onClick = {
                                                if (searchMode == SearchMode.VIDEO) {
                                                    videoViewModel.onQueryChange(suggestion)
                                                    videoViewModel.performSearch()
                                                } else {
                                                    musicViewModel.onQueryChange(suggestion)
                                                    musicViewModel.performSearch()
                                                }
                                            },
                                            icon = if (hasLiveSuggestions) {
                                                {
                                                    Icon(
                                                        Icons.Default.Search,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else null,
                                            label = {
                                                Text(
                                                    text = suggestion,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(22.dp),
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (hasLiveSuggestions) {
                                                    // YouTube live suggestions - use tertiary color
                                                    MaterialTheme.colorScheme.tertiaryContainer
                                                } else if (searchMode == SearchMode.VIDEO) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                },
                                                labelColor = if (hasLiveSuggestions) {
                                                    MaterialTheme.colorScheme.onTertiaryContainer
                                                } else if (searchMode == SearchMode.VIDEO) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                }
                                            )
                                        )
                                    }
                                }
                            }

                            // Recent searches (only when empty query and video mode)
                            if (currentQuery.isBlank() && searchMode == SearchMode.VIDEO && videoUiState.recentSearches.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Recent Searches",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                items(videoUiState.recentSearches.take(5)) { recentQuery ->
                                    ListItem(
                                        headlineContent = { Text(recentQuery) },
                                        leadingContent = {
                                            Icon(Icons.Default.History, "Recent")
                                        },
                                        modifier = Modifier.clickable {
                                            videoViewModel.onQueryChange(recentQuery)
                                            videoViewModel.performSearch()
                                        }
                                    )
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }

                    SearchUIState.LOADING -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    SearchUIState.BLOCKED -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Block,
                                        contentDescription = "Blocked",
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (searchMode == SearchMode.VIDEO) {
                                            videoUiState.searchBlockedMessage ?: "Search blocked"
                                        } else {
                                            musicUiState.searchBlockedMessage ?: "Search blocked"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = if (searchMode == SearchMode.VIDEO) {
                                            "Try searching for kid-friendly content like cartoons, nursery rhymes, or educational videos"
                                        } else {
                                            "Try searching for kid-friendly music like nursery rhymes, children's songs, or lullabies"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    SearchUIState.RESULTS -> {
                        if (searchMode == SearchMode.VIDEO) {
                            // Video results
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Did you mean correction
                                if (videoUiState.correctedQuery != null) {
                                    item {
                                        Card(
                                            onClick = { videoViewModel.searchWithCorrection() },
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Search,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Column {
                                                    Text(
                                                        text = "Did you mean:",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                    Text(
                                                        text = videoUiState.correctedQuery.orEmpty(),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (videoUiState.searchResults.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SearchOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = "No results found",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Try different keywords",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    item {
                                        Text(
                                            text = "${videoUiState.searchResults.size} results",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    items(videoUiState.searchResults) { video ->
                                        SearchResultItem(
                                            video = video,
                                            onClick = { onVideoClick(video.id) }
                                        )
                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(80.dp))
                                    }
                                }
                            }
                        } else {
                            // Music results
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (musicUiState.searchResults.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SearchOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = "No music found",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Try different artists, songs, or albums",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    // Extract all tracks from search results for queue
                                    val allTracks = musicUiState.searchResults
                                        .filterIsInstance<MusicSearchResult.TrackResult>()
                                        .map { it.track }

                                    item {
                                        Text(
                                            text = "${musicUiState.searchResults.size} results",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    items(musicUiState.searchResults) { result ->
                                        MusicSearchResultItem(
                                            result = result,
                                            currentPlayingTrackId = currentPlayingTrackId,
                                            onTrackClick = { trackId ->
                                                // Find the index of this track in the list
                                                val trackIndex = allTracks.indexOfFirst { it.id == trackId }
                                                if (trackIndex >= 0 && allTracks.isNotEmpty()) {
                                                    // Play with search results as queue
                                                    onPlayTracks(allTracks, trackIndex)
                                                } else {
                                                    // Fallback to single track
                                                    onTrackClick(trackId)
                                                }
                                            },
                                            onArtistClick = onArtistClick,
                                            onAlbumClick = onAlbumClick
                                        )
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
    }
}

/**
 * Music search result item
 */
@Composable
private fun MusicSearchResultItem(
    result: MusicSearchResult,
    currentPlayingTrackId: String?,
    onTrackClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit
) {
    when (result) {
        is MusicSearchResult.TrackResult -> {
            val track = result.track
            val isPlaying = track.id == currentPlayingTrackId
            Log.d("SearchScreen", "Rendering track: ${track.title} with id: ${track.id}, isPlaying: $isPlaying")
            Surface(
                onClick = {
                    Log.d("SearchScreen", "Track clicked: ${track.id}")
                    onTrackClick(track.id)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "${track.title} by ${track.artistName}" },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = "Song",
                            modifier = Modifier.size(14.dp),
                            tint = if (isPlaying) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = track.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPlaying) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = formatDuration(track.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Now playing indicator or play button
                if (isPlaying) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Now playing",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                }
            }
        }

        is MusicSearchResult.ArtistResult -> {
            val artist = result.artist
            Surface(
                onClick = { onArtistClick(artist.id) },
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Artist ${artist.name}" },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = artist.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Artist",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = artist.subscriberCount ?: "Artist",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        is MusicSearchResult.AlbumResult -> {
            val album = result.album
            Surface(
                onClick = { onAlbumClick(album.id) },
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Album ${album.title} by ${album.artistName}" },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = album.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Album,
                                contentDescription = "Album",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = album.artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            album.year?.let { year ->
                                Text(
                                    text = " • $year",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        is MusicSearchResult.PlaylistResult -> {
            val playlist = result.playlist
            Surface(
                onClick = { /* Handle YouTube Music playlist click */ },
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Playlist • ${playlist.trackCount} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "" // Don't show duration if not available
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Spotify/YouTube Music style search result item
 */
@Composable
private fun SearchResultItem(
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
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with rounded corners
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .size(100.dp, 56.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
