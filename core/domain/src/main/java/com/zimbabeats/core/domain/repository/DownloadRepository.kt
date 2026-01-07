package com.zimbabeats.core.domain.repository

import com.zimbabeats.core.domain.model.DownloadQueueItem
import com.zimbabeats.core.domain.model.DownloadStatus
import com.zimbabeats.core.domain.model.DownloadedVideo
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.VideoQuality
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    // Downloaded videos
    fun getAllDownloadedVideos(): Flow<List<Video>>
    fun getDownloadedVideo(videoId: String): Flow<DownloadedVideo?>
    fun isVideoDownloaded(videoId: String): Flow<Boolean>
    fun getTotalDownloadSize(): Flow<Long>

    // Download queue
    fun getAllQueueItems(): Flow<List<DownloadQueueItem>>
    fun getQueueItemsByStatus(status: DownloadStatus): Flow<List<DownloadQueueItem>>
    fun getQueueItem(videoId: String): Flow<DownloadQueueItem?>

    // Download operations
    suspend fun queueDownload(videoId: String, quality: VideoQuality): Resource<Unit>
    suspend fun pauseDownload(videoId: String): Resource<Unit>
    suspend fun resumeDownload(videoId: String): Resource<Unit>
    suspend fun cancelDownload(videoId: String): Resource<Unit>
    suspend fun updateDownloadProgress(videoId: String, progress: Int): Resource<Unit>
    suspend fun completeDownload(videoId: String, filePath: String, fileSize: Long, thumbnailPath: String?): Resource<Unit>
    suspend fun markDownloadFailed(videoId: String, error: String): Resource<Unit>

    // Cleanup
    suspend fun deleteDownload(videoId: String): Resource<Unit>
    suspend fun deleteExpiredDownloads(): Resource<Unit>
    suspend fun clearCompletedQueueItems(): Resource<Unit>
}
