package com.zimbabeats.core.data.mapper

import com.zimbabeats.core.data.local.entity.VideoEntity
import com.zimbabeats.core.data.remote.youtube.YouTubeVideo
import com.zimbabeats.core.domain.model.AgeRating
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.VideoCategory

/**
 * Convert YouTubeVideo (from NewPipe) to domain Video model
 */
fun YouTubeVideo.toDomainModel(): Video = Video(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    channelName = channelName,
    channelId = channelId,
    duration = duration,
    viewCount = viewCount,
    publishedAt = publishedAt,
    isKidFriendly = determineKidFriendly(this),
    ageRating = determineAgeRating(ageLimit),
    category = mapCategory(category),
    addedAt = System.currentTimeMillis(),
    lastAccessedAt = System.currentTimeMillis()
)

/**
 * Convert YouTubeVideo to VideoEntity for local storage
 */
fun YouTubeVideo.toEntity(): VideoEntity = VideoEntity(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    channelName = channelName,
    channelId = channelId,
    duration = duration,
    viewCount = viewCount,
    publishedAt = publishedAt,
    isKidFriendly = determineKidFriendly(this),
    ageRating = determineAgeRating(ageLimit).toEntityString(),
    category = category,
    addedAt = System.currentTimeMillis(),
    lastAccessedAt = System.currentTimeMillis()
)

/**
 * Determine if video is kid-friendly based on age limit and content
 */
private fun determineKidFriendly(video: YouTubeVideo): Boolean {
    // Not kid-friendly if age restricted (18+)
    if (video.ageLimit >= 18) return false

    // Check title/description for inappropriate keywords
    val inappropriateKeywords = listOf(
        "explicit", "18+", "nsfw", "mature", "adult",
        "violence", "horror", "scary"
    )

    val content = "${video.title} ${video.description}".lowercase()
    return !inappropriateKeywords.any { keyword -> content.contains(keyword) }
}

/**
 * Map age limit to AgeRating enum
 */
private fun determineAgeRating(ageLimit: Int): AgeRating = when {
    ageLimit == 0 -> AgeRating.ALL
    ageLimit <= 5 -> AgeRating.FIVE_PLUS
    ageLimit <= 10 -> AgeRating.TEN_PLUS
    ageLimit <= 12 -> AgeRating.TWELVE_PLUS
    else -> AgeRating.FOURTEEN_PLUS
}

/**
 * Map YouTube category to VideoCategory enum
 */
private fun mapCategory(category: String?): VideoCategory? {
    if (category == null) return null

    return when (category.lowercase()) {
        "education" -> VideoCategory.EDUCATION
        "entertainment" -> VideoCategory.ENTERTAINMENT
        "music" -> VideoCategory.MUSIC
        "science" -> VideoCategory.SCIENCE
        "animation" -> VideoCategory.ANIMATION
        "gaming", "games" -> VideoCategory.GAMES
        else -> null
    }
}
