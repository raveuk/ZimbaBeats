package com.zimbabeats.core.domain.model

data class SearchHistory(
    val id: Long = 0,
    val query: String,
    val searchedAt: Long,
    val resultCount: Int
)

data class SearchSuggestion(
    val suggestion: String,
    val frequency: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)

data class SearchResult(
    val query: String,
    val videos: List<Video>,
    val totalResults: Int,
    val hasMore: Boolean,
    val correctedQuery: String? = null // "Did you mean" spelling correction
)
