package com.zimbabeats.core.data.local.model

import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.VideoProgress
import com.zimbabeats.core.domain.model.AgeRating
import com.zimbabeats.core.domain.model.VideoCategory

/**
 * Tuple for JOIN query between videos, watch_history, and video_progress tables.
 * Used to fetch watch history with resume progress in a single query.
 */
data class VideoWithProgressTuple(
    // VideoEntity fields
    val id: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String,
    val channelName: String,
    val channelId: String,
    val duration: Long,
    val viewCount: Long,
    val publishedAt: Long,
    val isKidFriendly: Boolean,
    val ageRating: String,
    val category: String?,
    val addedAt: Long,
    val lastAccessedAt: Long,
    // Progress fields (nullable from LEFT JOIN)
    val currentPosition: Long?,
    val progressDuration: Long?,
    val progressLastUpdated: Long?
) {
    fun toDomain(): Video {
        val progress = if (currentPosition != null && progressDuration != null && progressDuration > 0) {
            VideoProgress(
                currentPosition = currentPosition,
                duration = progressDuration,
                lastUpdated = progressLastUpdated ?: System.currentTimeMillis()
            )
        } else null

        return Video(
            id = id,
            title = title,
            description = description,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            channelId = channelId,
            duration = duration,
            viewCount = viewCount,
            publishedAt = publishedAt,
            isKidFriendly = isKidFriendly,
            ageRating = try {
                AgeRating.valueOf(ageRating)
            } catch (e: IllegalArgumentException) {
                AgeRating.ALL
            },
            category = category?.let {
                try {
                    VideoCategory.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            },
            addedAt = addedAt,
            lastAccessedAt = lastAccessedAt,
            isFavorite = false,
            isDownloaded = false,
            progress = progress
        )
    }
}
