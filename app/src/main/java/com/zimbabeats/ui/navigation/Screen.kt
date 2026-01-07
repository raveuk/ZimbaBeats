package com.zimbabeats.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation destinations using type-safe navigation with Kotlin Serialization
 * Following modern Compose Navigation patterns
 */
@Serializable
sealed class Screen {
    @Serializable
    data object Onboarding : Screen()

    @Serializable
    data object Home : Screen()

    @Serializable
    data class Search(val mode: String = "VIDEO") : Screen()

    @Serializable
    data object Playlists : Screen()

    @Serializable
    data object Downloads : Screen()

    @Serializable
    data object Favorites : Screen()

    @Serializable
    data object WatchHistory : Screen()

    @Serializable
    data object Library : Screen()

    @Serializable
    data class VideoPlayer(val videoId: String) : Screen()

    @Serializable
    data class PlaylistDetail(val playlistId: Long) : Screen()

    @Serializable
    data object Settings : Screen()

    @Serializable
    data object ParentalControl : Screen()

    @Serializable
    data object ParentalDashboard : Screen()

    @Serializable
    data object Pairing : Screen()

    @Serializable
    data class PinEntry(val action: String) : Screen()

    // ==================== Music Navigation ====================

    @Serializable
    data object MusicHome : Screen()

    @Serializable
    data class MusicPlayer(val trackId: String) : Screen()

    @Serializable
    data class ArtistDetail(val artistId: String) : Screen()

    @Serializable
    data class AlbumDetail(val albumId: String) : Screen()

    @Serializable
    data class MusicPlaylistDetail(val playlistId: Long) : Screen()

    @Serializable
    data class YouTubeMusicPlaylistDetail(val playlistId: String) : Screen()

    @Serializable
    data object MusicLibrary : Screen()

    @Serializable
    data object MusicFavorites : Screen()
}
