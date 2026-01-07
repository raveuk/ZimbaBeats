package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.SearchSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchSuggestionDao {
    @Query("SELECT * FROM search_suggestions ORDER BY frequency DESC, lastUsed DESC LIMIT :limit")
    fun getTopSuggestions(limit: Int = 10): Flow<List<SearchSuggestionEntity>>

    @Query("SELECT * FROM search_suggestions WHERE suggestion LIKE :query || '%' ORDER BY frequency DESC LIMIT :limit")
    fun getSuggestionsByQuery(query: String, limit: Int = 5): Flow<List<SearchSuggestionEntity>>

    @Query("SELECT * FROM search_suggestions WHERE suggestion = :suggestion")
    suspend fun getSuggestion(suggestion: String): SearchSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: SearchSuggestionEntity)

    @Query("UPDATE search_suggestions SET frequency = frequency + 1, lastUsed = :timestamp WHERE suggestion = :suggestion")
    suspend fun incrementFrequency(suggestion: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteSuggestion(suggestion: SearchSuggestionEntity)

    @Query("DELETE FROM search_suggestions WHERE lastUsed < :timestamp")
    suspend fun deleteOldSuggestions(timestamp: Long)

    @Query("DELETE FROM search_suggestions")
    suspend fun deleteAllSuggestions()
}
