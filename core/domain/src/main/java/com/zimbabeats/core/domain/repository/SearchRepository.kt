package com.zimbabeats.core.domain.repository

import com.zimbabeats.core.domain.model.SearchHistory
import com.zimbabeats.core.domain.model.SearchResult
import com.zimbabeats.core.domain.model.SearchSuggestion
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    // Kid-safe mode
    fun setKidSafeMode(enabled: Boolean)

    // Search videos (local)
    fun searchVideosLocally(query: String): Flow<List<Video>>

    // Search videos (remote - YouTube API)
    suspend fun searchVideos(query: String, maxResults: Int = 20): Resource<SearchResult>

    // Get YouTube autocomplete suggestions (helps with typos)
    suspend fun getYouTubeSuggestions(query: String): Resource<List<String>>

    // Search history
    fun getRecentSearches(limit: Int = 20): Flow<List<SearchHistory>>
    fun getRecentSearchQueries(limit: Int = 10): Flow<List<String>>
    suspend fun saveSearchHistory(query: String, resultCount: Int): Resource<Unit>
    suspend fun deleteSearchHistory(query: String): Resource<Unit>
    suspend fun clearSearchHistory(): Resource<Unit>

    // Search suggestions
    fun getTopSuggestions(limit: Int = 10): Flow<List<SearchSuggestion>>
    fun getSuggestionsByQuery(query: String, limit: Int = 5): Flow<List<SearchSuggestion>>
    suspend fun updateSuggestion(suggestion: String): Resource<Unit>
    suspend fun clearSuggestions(): Resource<Unit>
}
