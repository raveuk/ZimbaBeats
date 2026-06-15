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
    val tracks: List<Track> = emptyList(),   // Music tracks
    // Sharing fields
    val shareCode: String? = null,           // 6-char share code if shared
    val sharedAt: Long? = null,              // When share code was generated
    val isImported: Boolean = false,         // True if imported from another kid
    val importedFrom: String? = null,        // Name of kid who shared it
    val importedAt: Long? = null             // When it was imported
) {
    /** Total number of items (videos + tracks) */
    val totalItemCount: Int get() = videoCount + trackCount

    /** Whether this playlist has an active share code */
    val isShared: Boolean get() = shareCode != null
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
