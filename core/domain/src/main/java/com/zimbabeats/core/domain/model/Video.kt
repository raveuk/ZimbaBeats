package com.zimbabeats.core.domain.model

data class Video(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String,
    val channelName: String,
    val channelId: String,
    val duration: Long,
    val viewCount: Long,
    val publishedAt: Long,
    val isKidFriendly: Boolean = true,
    val ageRating: AgeRating = AgeRating.ALL,
    val category: VideoCategory?,
    val addedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val progress: VideoProgress? = null
)

enum class AgeRating(val displayName: String, val ageLimit: Int) {
    ALL("All Ages", 0),
    FIVE_PLUS("Kids Under 5", 5),
    EIGHT_PLUS("Kids Under 8", 8),
    TEN_PLUS("Kids Under 10", 10),
    TWELVE_PLUS("Kids Under 12", 12),
    THIRTEEN_PLUS("Kids Under 13", 13),
    FOURTEEN_PLUS("Kids Under 14", 14),
    SIXTEEN_PLUS("Kids Under 16", 16)
}

enum class VideoCategory {
    EDUCATION,
    ENTERTAINMENT,
    MUSIC,
    SCIENCE,
    ART_CRAFTS,
    STORIES,
    KIDS_SONGS,
    ANIMATION,
    LEARNING,
    GAMES
}

data class VideoProgress(
    val currentPosition: Long,
    val duration: Long,
    val lastUpdated: Long
) {
    val progressPercentage: Float
        get() = if (duration > 0) (currentPosition.toFloat() / duration) else 0f
}
