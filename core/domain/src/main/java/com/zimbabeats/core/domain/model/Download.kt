package com.zimbabeats.core.domain.model

data class DownloadedVideo(
    val videoId: String,
    val filePath: String,
    val fileSize: Long,
    val quality: VideoQuality,
    val downloadedAt: Long,
    val expiresAt: Long? = null,
    val thumbnailPath: String?
)

data class DownloadQueueItem(
    val videoId: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val quality: VideoQuality,
    val queuedAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val error: String? = null,
    val video: Video? = null
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    FAILED,
    COMPLETED
}

enum class VideoQuality {
    LOW_144P,
    SD_360P,
    SD_480P,
    HD_720P,
    HD_1080P;

    val displayName: String
        get() = when (this) {
            LOW_144P -> "144p"
            SD_360P -> "360p"
            SD_480P -> "480p"
            HD_720P -> "720p"
            HD_1080P -> "1080p"
        }
}
