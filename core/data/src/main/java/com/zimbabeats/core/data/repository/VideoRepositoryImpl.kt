package com.zimbabeats.core.data.repository

import com.zimbabeats.core.data.local.database.ZimbaBeatsDatabase
import com.zimbabeats.core.data.local.entity.FavoriteVideoEntity
import com.zimbabeats.core.data.local.entity.VideoProgressEntity
import com.zimbabeats.core.data.local.entity.WatchHistoryEntity
import com.zimbabeats.core.data.mapper.toDomain
import com.zimbabeats.core.data.mapper.toDomainModel
import com.zimbabeats.core.data.mapper.toEntity
import com.zimbabeats.core.data.remote.youtube.YouTubeException
import com.zimbabeats.core.data.remote.youtube.YouTubeService
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.VideoCategory
import com.zimbabeats.core.domain.model.VideoProgress
import com.zimbabeats.core.domain.repository.VideoRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class VideoRepositoryImpl(
    private val database: ZimbaBeatsDatabase,
    private val youTubeService: YouTubeService
) : VideoRepository {

    private val videoDao = database.videoDao()
    private val videoProgressDao = database.videoProgressDao()
    private val favoriteVideoDao = database.favoriteVideoDao()
    private val watchHistoryDao = database.watchHistoryDao()
    private val downloadedVideoDao = database.downloadedVideoDao()

    override fun getAllVideos(): Flow<List<Video>> = combine(
        videoDao.getAllVideos(),
        favoriteVideoDao.getAllFavoriteVideos().map { it.map { v -> v.id } },
        downloadedVideoDao.getAllDownloadedVideos().map { it.map { v -> v.id } }
    ) { videos, favoriteIds, downloadedIds ->
        videos.map { entity ->
            entity.toDomain(
                isFavorite = favoriteIds.contains(entity.id),
                isDownloaded = downloadedIds.contains(entity.id)
            )
        }
    }

    override fun getVideoById(videoId: String): Flow<Video?> = combine(
        videoDao.getVideoById(videoId),
        favoriteVideoDao.isFavorite(videoId),
        downloadedVideoDao.isVideoDownloaded(videoId),
        videoProgressDao.getProgress(videoId)
    ) { entity, isFavorite, isDownloaded, progress ->
        entity?.toDomain(
            isFavorite = isFavorite,
            isDownloaded = isDownloaded,
            progress = progress?.toDomain()
        )
    }

    override fun getKidFriendlyVideos(limit: Int): Flow<List<Video>> =
        videoDao.getKidFriendlyVideos(limit).map { videos ->
            videos.map { it.toDomain() }
        }

    override fun getVideosByCategory(category: VideoCategory): Flow<List<Video>> =
        videoDao.getVideosByCategory(category.name).map { videos ->
            videos.map { it.toDomain() }
        }

    override fun getVideosByChannel(channelId: String): Flow<List<Video>> =
        videoDao.getVideosByChannel(channelId).map { videos ->
            videos.map { it.toDomain() }
        }

    override suspend fun saveVideo(video: Video): Resource<Unit> = try {
        videoDao.insertVideo(video.toEntity())
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to save video: ${e.message}", e)
    }

    override suspend fun saveVideos(videos: List<Video>): Resource<Unit> = try {
        videoDao.insertVideos(videos.map { it.toEntity() })
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to save videos: ${e.message}", e)
    }

    override suspend fun updateLastAccessed(videoId: String): Resource<Unit> = try {
        videoDao.updateLastAccessed(videoId)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to update last accessed: ${e.message}", e)
    }

    override suspend fun deleteVideo(videoId: String): Resource<Unit> = try {
        videoDao.deleteVideoById(videoId)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to delete video: ${e.message}", e)
    }

    override suspend fun clearAllCachedVideos(): Resource<Unit> = try {
        videoDao.deleteAllVideos()
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to clear cached videos: ${e.message}", e)
    }

    override fun getVideoProgress(videoId: String): Flow<VideoProgress?> =
        videoProgressDao.getProgress(videoId).map { it?.toDomain() }

    override suspend fun saveVideoProgress(videoId: String, position: Long, duration: Long): Resource<Unit> = try {
        val progress = VideoProgressEntity(
            videoId = videoId,
            currentPosition = position,
            duration = duration,
            lastUpdated = System.currentTimeMillis()
        )
        videoProgressDao.insertProgress(progress)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to save video progress: ${e.message}", e)
    }

    override suspend fun deleteVideoProgress(videoId: String): Resource<Unit> = try {
        videoProgressDao.deleteProgressForVideo(videoId)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to delete video progress: ${e.message}", e)
    }

    override fun getFavoriteVideos(): Flow<List<Video>> =
        favoriteVideoDao.getAllFavoriteVideos().map { videos ->
            videos.map { it.toDomain(isFavorite = true) }
        }

    override fun isVideoFavorite(videoId: String): Flow<Boolean> =
        favoriteVideoDao.isFavorite(videoId)

    override suspend fun addToFavorites(videoId: String): Resource<Unit> = try {
        val favorite = FavoriteVideoEntity(
            videoId = videoId,
            addedAt = System.currentTimeMillis()
        )
        favoriteVideoDao.insertFavorite(favorite)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to add to favorites: ${e.message}", e)
    }

    override suspend fun removeFromFavorites(videoId: String): Resource<Unit> = try {
        favoriteVideoDao.deleteFavorite(videoId)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to remove from favorites: ${e.message}", e)
    }

    override suspend fun toggleFavorite(videoId: String): Resource<Unit> = try {
        val existing = favoriteVideoDao.getFavoriteVideo(videoId)
        if (existing != null) {
            favoriteVideoDao.deleteFavorite(videoId)
        } else {
            favoriteVideoDao.insertFavorite(
                FavoriteVideoEntity(
                    videoId = videoId,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to toggle favorite: ${e.message}", e)
    }

    override fun getWatchHistory(limit: Int): Flow<List<Video>> =
        watchHistoryDao.getRecentlyWatchedWithProgress(limit).map { tuples ->
            tuples.map { it.toDomain() }
        }

    override fun getMostWatched(limit: Int): Flow<List<Video>> =
        watchHistoryDao.getMostWatched(limit).map { tuples ->
            tuples.map { it.toDomain() }
        }

    override suspend fun addToWatchHistory(
        videoId: String,
        watchDuration: Long,
        completionPercentage: Float,
        profileId: Long?
    ): Resource<Unit> = try {
        // Check if video already exists in history
        val existingCount = watchHistoryDao.getWatchCount(videoId)
        if (existingCount != null) {
            // Video already watched before - increment watch count and update timestamp
            watchHistoryDao.incrementWatchCount(videoId)
        } else {
            // First time watching - insert new entry with watchCount = 1
            val history = WatchHistoryEntity(
                videoId = videoId,
                lastWatchedAt = System.currentTimeMillis(),
                watchDuration = watchDuration,
                completionPercentage = completionPercentage,
                profileId = profileId,
                watchCount = 1
            )
            watchHistoryDao.insertHistory(history)
        }
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to add to watch history: ${e.message}", e)
    }

    override suspend fun clearWatchHistory(): Resource<Unit> = try {
        watchHistoryDao.deleteAllHistory()
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to clear watch history: ${e.message}", e)
    }

    override suspend fun removeFromWatchHistory(videoId: String): Resource<Unit> = try {
        watchHistoryDao.deleteHistoryForVideo(videoId)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to remove from watch history: ${e.message}", e)
    }

    override suspend fun fetchTrendingVideos(maxResults: Int): Resource<List<Video>> = try {
        // Fetch trending videos from YouTube
        val youtubeVideos = youTubeService.getTrendingVideos(maxResults)

        // Convert to domain models
        val videos = youtubeVideos.map { it.toDomainModel() }

        // Save to local database for caching
        val entities = youtubeVideos.map { it.toEntity() }
        videoDao.insertVideos(entities)

        Resource.success(videos)
    } catch (e: YouTubeException) {
        Resource.error("Failed to fetch trending videos: ${e.message}", e)
    } catch (e: Exception) {
        Resource.error("Failed to fetch trending videos: ${e.message}", e)
    }
}
