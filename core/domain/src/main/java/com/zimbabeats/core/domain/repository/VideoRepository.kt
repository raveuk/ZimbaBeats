package com.zimbabeats.core.domain.repository

import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.VideoCategory
import com.zimbabeats.core.domain.model.VideoProgress
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    // Get videos
    fun getAllVideos(): Flow<List<Video>>
    fun getVideoById(videoId: String): Flow<Video?>
    fun getKidFriendlyVideos(limit: Int): Flow<List<Video>>
    fun getVideosByCategory(category: VideoCategory): Flow<List<Video>>
    fun getVideosByChannel(channelId: String): Flow<List<Video>>

    // Fetch from YouTube
    suspend fun fetchTrendingVideos(maxResults: Int = 20): Resource<List<Video>>

    // Save/Update videos
    suspend fun saveVideo(video: Video): Resource<Unit>
    suspend fun saveVideos(videos: List<Video>): Resource<Unit>
    suspend fun updateLastAccessed(videoId: String): Resource<Unit>
    suspend fun deleteVideo(videoId: String): Resource<Unit>
    suspend fun clearAllCachedVideos(): Resource<Unit>

    // Video progress
    fun getVideoProgress(videoId: String): Flow<VideoProgress?>
    suspend fun saveVideoProgress(videoId: String, position: Long, duration: Long): Resource<Unit>
    suspend fun deleteVideoProgress(videoId: String): Resource<Unit>

    // Favorites
    fun getFavoriteVideos(): Flow<List<Video>>
    fun isVideoFavorite(videoId: String): Flow<Boolean>
    suspend fun addToFavorites(videoId: String): Resource<Unit>
    suspend fun removeFromFavorites(videoId: String): Resource<Unit>
    suspend fun toggleFavorite(videoId: String): Resource<Unit>

    // Watch history
    fun getWatchHistory(limit: Int = 50): Flow<List<Video>>
    fun getMostWatched(limit: Int = 10): Flow<List<Video>>
    suspend fun addToWatchHistory(videoId: String, watchDuration: Long, completionPercentage: Float, profileId: Long?): Resource<Unit>
    suspend fun clearWatchHistory(): Resource<Unit>
    suspend fun removeFromWatchHistory(videoId: String): Resource<Unit>
}
