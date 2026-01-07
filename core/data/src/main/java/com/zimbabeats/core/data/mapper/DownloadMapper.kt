package com.zimbabeats.core.data.mapper

import com.zimbabeats.core.data.local.entity.DownloadQueueEntity
import com.zimbabeats.core.data.local.entity.DownloadedVideoEntity
import com.zimbabeats.core.domain.model.DownloadQueueItem
import com.zimbabeats.core.domain.model.DownloadStatus
import com.zimbabeats.core.domain.model.DownloadedVideo
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.VideoQuality

fun DownloadedVideoEntity.toDomain(): DownloadedVideo = DownloadedVideo(
    videoId = videoId,
    filePath = filePath,
    fileSize = fileSize,
    quality = quality.toVideoQuality(),
    downloadedAt = downloadedAt,
    expiresAt = expiresAt,
    thumbnailPath = thumbnailPath
)

fun DownloadQueueEntity.toDomain(video: Video? = null): DownloadQueueItem = DownloadQueueItem(
    videoId = videoId,
    status = status.toDownloadStatus(),
    progress = (progress * 100).toInt(),
    quality = quality.toVideoQuality(),
    queuedAt = queuedAt,
    startedAt = startedAt,
    completedAt = completedAt,
    error = error,
    video = video
)

fun DownloadQueueItem.toEntity(): DownloadQueueEntity = DownloadQueueEntity(
    videoId = videoId,
    status = status.name,
    progress = progress / 100f,
    quality = quality.name,
    queuedAt = queuedAt,
    startedAt = startedAt,
    completedAt = completedAt,
    error = error
)

fun String.toDownloadStatus(): DownloadStatus = try {
    DownloadStatus.valueOf(this)
} catch (e: IllegalArgumentException) {
    DownloadStatus.PENDING
}

fun String.toVideoQuality(): VideoQuality = try {
    VideoQuality.valueOf(this)
} catch (e: IllegalArgumentException) {
    VideoQuality.SD_360P
}
