package com.zimbabeats.cloud.model

/**
 * Data classes for playlist sharing feature.
 */

/**
 * Full shared playlist data with all content for import.
 */
data class SharedPlaylistData(
    val shareCode: String,
    val playlistName: String,
    val description: String?,
    val color: String,
    val sharedByChildName: String,
    val sharedByFamilyId: String,
    val videos: List<SharedVideoInfo>,
    val tracks: List<SharedTrackInfo>
)

/**
 * Preview of a shared playlist before importing.
 */
data class SharedPlaylistPreview(
    val shareCode: String,
    val playlistName: String,
    val description: String?,
    val sharedByChildName: String,
    val videoCount: Int,
    val trackCount: Int,
    val expiresAt: Long,
    val redeemCount: Int,
    val maxRedeems: Int
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    val canRedeem: Boolean get() = !isExpired && redeemCount < maxRedeems
    val totalItemCount: Int get() = videoCount + trackCount
}

/**
 * Info about a playlist the current user has shared.
 */
data class SharedPlaylistInfo(
    val shareCode: String,
    val playlistId: Long,
    val playlistName: String,
    val sharedAt: Long,
    val expiresAt: Long,
    val redeemCount: Int,
    val maxRedeems: Int,
    val isActive: Boolean
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
}

/**
 * Video info stored in shared playlist.
 */
data class SharedVideoInfo(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long
)

/**
 * Track info stored in shared playlist.
 */
data class SharedTrackInfo(
    val trackId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val albumName: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long
)

/**
 * Result of sharing operation.
 */
sealed class ShareResult {
    data class Success(val shareCode: String) : ShareResult()
    data class Error(val message: String) : ShareResult()
}

/**
 * Result of import operation.
 */
sealed class ImportResult {
    data class Success(val playlistId: Long, val itemsImported: Int, val itemsFiltered: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * Result of code validation.
 */
sealed class ValidateResult {
    data class Valid(val preview: SharedPlaylistPreview) : ValidateResult()
    data class Invalid(val reason: String) : ValidateResult()
    data class Error(val message: String) : ValidateResult()
}
