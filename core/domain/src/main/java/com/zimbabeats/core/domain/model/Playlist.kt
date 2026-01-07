package com.zimbabeats.core.domain.model

import com.zimbabeats.core.domain.model.music.Track

data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val videoCount: Int = 0,
    val trackCount: Int = 0,                 // Music track count
    val isFavorite: Boolean = false,
    val color: PlaylistColor = PlaylistColor.PINK,
    val videos: List<Video> = emptyList(),
    val tracks: List<Track> = emptyList()    // Music tracks
) {
    /** Total number of items (videos + tracks) */
    val totalItemCount: Int get() = videoCount + trackCount
}

enum class PlaylistColor(val hex: String) {
    PINK("#FF6B9D"),
    BLUE("#4DA6FF"),
    GREEN("#66CC99"),
    ORANGE("#FFB366"),
    PURPLE("#B366FF"),
    YELLOW("#FFE666"),
    RED("#FF6B6B"),
    TEAL("#66CCCC")
}
