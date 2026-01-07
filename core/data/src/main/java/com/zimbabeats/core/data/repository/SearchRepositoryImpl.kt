package com.zimbabeats.core.data.repository

import com.zimbabeats.core.data.local.database.ZimbaBeatsDatabase
import com.zimbabeats.core.data.local.entity.SearchHistoryEntity
import com.zimbabeats.core.data.local.entity.SearchSuggestionEntity
import com.zimbabeats.core.data.mapper.toDomain
import com.zimbabeats.core.data.mapper.toDomainModel
import com.zimbabeats.core.data.mapper.toEntity
import com.zimbabeats.core.data.remote.youtube.YouTubeException
import com.zimbabeats.core.data.remote.youtube.YouTubeService
import com.zimbabeats.core.domain.model.SearchHistory
import com.zimbabeats.core.domain.model.SearchResult
import com.zimbabeats.core.domain.model.SearchSuggestion
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.repository.SearchRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SearchRepositoryImpl(
    private val database: ZimbaBeatsDatabase,
    private val youTubeService: YouTubeService
) : SearchRepository {

    private val videoDao = database.videoDao()
    private val searchHistoryDao = database.searchHistoryDao()
    private val searchSuggestionDao = database.searchSuggestionDao()

    /**
     * Enable or disable kid-safe search mode
     */
    override fun setKidSafeMode(enabled: Boolean) {
        youTubeService.setKidSafeMode(enabled)
    }

    override fun searchVideosLocally(query: String): Flow<List<Video>> =
        videoDao.searchVideos(query).map { videos ->
            videos.map { it.toDomain() }
        }

    override suspend fun searchVideos(query: String, maxResults: Int): Resource<SearchResult> = try {
        // Search YouTube using Innertube API with correction support
        val searchResult = youTubeService.searchVideosWithCorrection(query, maxResults)

        // Convert to domain models
        val videos = searchResult.videos.map { it.toDomainModel() }

        // Save to database so videos can be played when clicked
        val entities = searchResult.videos.map { it.toEntity() }
        videoDao.insertVideos(entities)

        // Create search result with correction info
        val result = SearchResult(
            query = query,
            videos = videos,
            totalResults = videos.size,
            hasMore = videos.size >= maxResults,
            correctedQuery = searchResult.correctedQuery
        )

        Resource.success(result)
    } catch (e: YouTubeException) {
        Resource.error("YouTube search failed: ${e.message}", e)
    } catch (e: Exception) {
        Resource.error("Search failed: ${e.message}", e)
    }

    override suspend fun getYouTubeSuggestions(query: String): Resource<List<String>> = try {
        val suggestions = youTubeService.getSearchSuggestions(query)
        Resource.success(suggestions)
    } catch (e: Exception) {
        Resource.error("Failed to get suggestions: ${e.message}", e)
    }

    override fun getRecentSearches(limit: Int): Flow<List<SearchHistory>> =
        searchHistoryDao.getRecentSearches(limit).map { searches ->
            searches.map { SearchHistory(it.id, it.query, it.searchedAt, it.resultCount) }
        }

    override fun getRecentSearchQueries(limit: Int): Flow<List<String>> =
        searchHistoryDao.getRecentSearchQueries(limit)

    override suspend fun saveSearchHistory(query: String, resultCount: Int): Resource<Unit> = try {
        val search = SearchHistoryEntity(
            query = query,
            searchedAt = System.currentTimeMillis(),
            resultCount = resultCount
        )
        searchHistoryDao.insertSearch(search)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to save search history: ${e.message}", e)
    }

    override suspend fun deleteSearchHistory(query: String): Resource<Unit> = try {
        searchHistoryDao.deleteSearchByQuery(query)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to delete search history: ${e.message}", e)
    }

    override suspend fun clearSearchHistory(): Resource<Unit> = try {
        searchHistoryDao.deleteAllSearchHistory()
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to clear search history: ${e.message}", e)
    }

    override fun getTopSuggestions(limit: Int): Flow<List<SearchSuggestion>> =
        searchSuggestionDao.getTopSuggestions(limit).map { suggestions ->
            suggestions.map { it.toDomain() }
        }

    override fun getSuggestionsByQuery(query: String, limit: Int): Flow<List<SearchSuggestion>> =
        searchSuggestionDao.getSuggestionsByQuery(query, limit).map { suggestions ->
            suggestions.map { it.toDomain() }
        }

    override suspend fun updateSuggestion(suggestion: String): Resource<Unit> = try {
        val existing = searchSuggestionDao.getSuggestion(suggestion)
        if (existing != null) {
            searchSuggestionDao.incrementFrequency(suggestion)
        } else {
            searchSuggestionDao.insertSuggestion(
                SearchSuggestionEntity(
                    suggestion = suggestion,
                    frequency = 1,
                    lastUsed = System.currentTimeMillis()
                )
            )
        }
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to update suggestion: ${e.message}", e)
    }

    override suspend fun clearSuggestions(): Resource<Unit> = try {
        searchSuggestionDao.deleteAllSuggestions()
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to clear suggestions: ${e.message}", e)
    }
}

private fun SearchSuggestionEntity.toDomain() = SearchSuggestion(
    suggestion = suggestion,
    frequency = frequency,
    lastUsed = lastUsed
)
