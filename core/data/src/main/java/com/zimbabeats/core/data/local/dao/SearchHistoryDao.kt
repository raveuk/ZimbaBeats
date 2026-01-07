package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentSearches(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("SELECT DISTINCT query FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun getRecentSearchQueries(limit: Int = 10): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchByQuery(query: String)

    @Query("DELETE FROM search_history WHERE searchedAt < :timestamp")
    suspend fun deleteOldSearches(timestamp: Long)

    @Query("DELETE FROM search_history")
    suspend fun deleteAllSearchHistory()

    @Query("SELECT COUNT(*) FROM search_history")
    fun getSearchCount(): Flow<Int>
}
