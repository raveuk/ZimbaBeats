package com.zimbabeats.core.data.mapper

import com.zimbabeats.core.data.local.entity.VideoEntity
import com.zimbabeats.core.data.local.entity.VideoProgressEntity
import com.zimbabeats.core.domain.model.AgeRating
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.VideoCategory
import com.zimbabeats.core.domain.model.VideoProgress

fun VideoEntity.toDomain(
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    progress: VideoProgress? = null
): Video = Video(
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
    ageRating = ageRating.toAgeRating(),
    category = category?.toVideoCategory(),
    addedAt = addedAt,
    lastAccessedAt = lastAccessedAt,
    isFavorite = isFavorite,
    isDownloaded = isDownloaded,
    progress = progress
)

fun Video.toEntity(): VideoEntity = VideoEntity(
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
    ageRating = ageRating.toEntityString(),
    category = category?.name,
    addedAt = addedAt,
    lastAccessedAt = lastAccessedAt
)

fun VideoProgressEntity.toDomain(): VideoProgress = VideoProgress(
    currentPosition = currentPosition,
    duration = duration,
    lastUpdated = lastUpdated
)

fun String.toAgeRating(): AgeRating = when (this) {
    "ALL" -> AgeRating.ALL
    "5+" -> AgeRating.FIVE_PLUS
    "8+" -> AgeRating.EIGHT_PLUS
    "13+" -> AgeRating.THIRTEEN_PLUS
    "16+" -> AgeRating.SIXTEEN_PLUS
    // Legacy support for old values (map to closest)
    "3+" -> AgeRating.FIVE_PLUS
    "7+" -> AgeRating.EIGHT_PLUS
    "10+" -> AgeRating.EIGHT_PLUS
    "12+" -> AgeRating.THIRTEEN_PLUS
    "14+" -> AgeRating.THIRTEEN_PLUS
    else -> AgeRating.ALL
}

fun AgeRating.toEntityString(): String = when (this) {
    AgeRating.ALL -> "ALL"
    AgeRating.FIVE_PLUS -> "5+"
    AgeRating.EIGHT_PLUS -> "8+"
    AgeRating.THIRTEEN_PLUS -> "13+"
    AgeRating.SIXTEEN_PLUS -> "16+"
}

fun String.toVideoCategory(): VideoCategory? = try {
    VideoCategory.valueOf(this)
} catch (e: IllegalArgumentException) {
    null
}
