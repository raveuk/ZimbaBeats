package com.zimbabeats.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class SharedVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String = "",
    val thumbnailUrl: String,
    val durationText: String = "",
    val viewCountText: String = ""
)

@Serializable
data class SharedStreamUrl(
    val url: String,
    val mimeType: String,
    val qualityLabel: String,
    val isAudioOnly: Boolean = false
)

enum class AgeRatingFilter(val label: String, val maxAge: Int) {
    UNDER_5("Under 5", 5),
    UNDER_8("Under 8", 8),
    UNDER_13("Under 13", 13),
    ALL("All Kids", 16)
}
