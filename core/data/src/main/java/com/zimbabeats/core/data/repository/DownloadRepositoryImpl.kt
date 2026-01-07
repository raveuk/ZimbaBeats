package com.zimbabeats.core.data.repository

import com.zimbabeats.core.data.local.database.ZimbaBeatsDatabase
import com.zimbabeats.core.data.local.entity.DownloadQueueEntity
import com.zimbabeats.core.data.local.entity.DownloadedVideoEntity
import com.zimbabeats.core.data.mapper.toDomain
import com.zimbabeats.core.domain.model.DownloadQueueItem
import com.zimbabeats.core.domain.model.DownloadStatus
import com.zimbabeats.core.domain.model.DownloadedVideo
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.VideoQuality
import com.zimbabeats.core.domain.repository.DownloadRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class DownloadRepositoryImpl(
    private val database: ZimbaBeatsDatabase
) : DownloadRepository {

    private val downloadedVideoDao = database.downloadedVideoDao()
    private val downloadQueueDao = database.downloadQueueDao()
    private val videoDao = database.videoDao()

    override fun getAllDownloadedVideos(): Flow<List<Video>> =
        downloadedVideoDao.getAllDownloadedVideos().map { videos ->
            videos.map { it.toDomain(isDownloaded = true) }
        }

    override fun getDownloadedVideo(videoId: String): Flow<DownloadedVideo?> =
        downloadedVideoDao.getDownloadedVideo(videoId).map { it?.toDomain() }

    override fun isVideoDownloaded(videoId: String): Flow<Boolean> =
        downloadedVideoDao.isVideoDownloaded(videoId)

    override fun getTotalDownloadSize(): Flow<Long> =
        downloadedVideoDao.getTotalDownloadSize().map { it ?: 0L }

    override fun getAllQueueItems(): Flow<List<DownloadQueueItem>> =
        combine(
            downloadQueueDao.getAllQueueItems(),
            videoDao.getAllVideos()
        ) { queueItems, videos ->
            queueItems.map { queueItem ->
                val video = videos.find { it.id == queueItem.videoId }
                queueItem.toDomain(video?.toDomain())
            }
        }

    override fun getQueueItemsByStatus(status: DownloadStatus): Flow<List<DownloadQueueItem>> =
        downloadQueueDao.getQueueItemsByStatus(status.name).map { items ->
            items.map { it.toDomain() }
        }

    override fun getQueueItem(videoId: String): Flow<DownloadQueueItem?> =
        downloadQueueDao.getQueueItem(videoId).map { it?.toDomain() }

    override suspend fun queueDownload(videoId: String, quality: VideoQuality): Resource<Unit> = try {
        val queueItem = DownloadQueueEntity(
            videoId = videoId,
            status = DownloadStatus.PENDING.name,
            progress = 0f,
            quality = quality.name,
            queuedAt = System.currentTimeMillis()
        )
        downloadQueueDao.insertQueueItem(queueItem)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to queue download: ${e.message}", e)
    }

    override suspend fun pauseDownload(videoId: String): Resource<Unit> = try {
        downloadQueueDao.updateStatus(videoId, DownloadStatus.PAUSED.name)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to pause download: ${e.message}", e)
    }

    override suspend fun resumeDownload(videoId: String): Resource<Unit> = try {
        downloadQueueDao.updateStatus(videoId, DownloadStatus.DOWNLOADING.name)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to resume download: ${e.message}", e)
    }

    override suspend fun cancelDownload(videoId: String): Resource<Unit> = try {
        downloadQueueDao.deleteQueueItemByVideoId(videoId)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to cancel download: ${e.message}", e)
    }

    override suspend fun updateDownloadProgress(videoId: String, progress: Int): Resource<Unit> = try {
        downloadQueueDao.updateProgress(videoId, progress)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to update download progress: ${e.message}", e)
    }

    override suspend fun completeDownload(
        videoId: String,
        filePath: String,
        fileSize: Long,
        thumbnailPath: String?
    ): Resource<Unit> = try {
        // Get quality from queue
        val queueItem = downloadQueueDao.getQueueItemSync(videoId)
        val quality = queueItem?.quality ?: VideoQuality.SD_360P.name

        // Save to downloaded videos
        val downloadedVideo = DownloadedVideoEntity(
            videoId = videoId,
            filePath = filePath,
            fileSize = fileSize,
            quality = quality,
            downloadedAt = System.currentTimeMillis(),
            expiresAt = null,
            thumbnailPath = thumbnailPath
        )
        downloadedVideoDao.insertDownload(downloadedVideo)

        // Update queue status
        downloadQueueDao.updateStatus(videoId, DownloadStatus.COMPLETED.name)

        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to complete download: ${e.message}", e)
    }

    override suspend fun markDownloadFailed(videoId: String, error: String): Resource<Unit> = try {
        downloadQueueDao.updateStatusWithError(videoId, DownloadStatus.FAILED.name, error)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to mark download as failed: ${e.message}", e)
    }

    override suspend fun deleteDownload(videoId: String): Resource<Unit> = try {
        downloadedVideoDao.deleteDownloadByVideoId(videoId)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to delete download: ${e.message}", e)
    }

    override suspend fun deleteExpiredDownloads(): Resource<Unit> = try {
        downloadedVideoDao.deleteExpiredDownloads()
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to delete expired downloads: ${e.message}", e)
    }

    override suspend fun clearCompletedQueueItems(): Resource<Unit> = try {
        downloadQueueDao.deleteCompletedItems()
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to clear completed queue items: ${e.message}", e)
    }
}
