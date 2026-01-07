package com.zimbabeats.core.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zimbabeats.core.data.local.dao.*
import com.zimbabeats.core.data.local.dao.music.*
import com.zimbabeats.core.data.local.entity.*
import com.zimbabeats.core.data.local.entity.music.*

@Database(
    entities = [
        // Video entities
        VideoEntity::class,
        WatchHistoryEntity::class,
        VideoProgressEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
        PlaylistTrackEntity::class,  // Unified playlist tracks (music)
        FavoriteVideoEntity::class,
        DownloadedVideoEntity::class,
        DownloadQueueEntity::class,
        SearchHistoryEntity::class,
        SearchSuggestionEntity::class,
        // Note: Parental control settings/profiles moved to companion app
        // ScreenTimeLogEntity kept for usage analytics
        ScreenTimeLogEntity::class,
        AppUsageEntity::class,
        // Music entities
        TrackEntity::class,
        MusicPlaylistEntity::class,
        MusicPlaylistTrackEntity::class,
        FavoriteTrackEntity::class,
        MusicListeningHistoryEntity::class
    ],
    version = 7,  // Bumped version for schema change
    exportSchema = false
)
abstract class ZimbaBeatsDatabase : RoomDatabase() {
    // Video & Playback
    abstract fun videoDao(): VideoDao
    abstract fun videoProgressDao(): VideoProgressDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteVideoDao(): FavoriteVideoDao

    // Playlists (unified - supports both videos and music)
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistVideoDao(): PlaylistVideoDao
    abstract fun playlistTrackDao(): PlaylistTrackDao

    // Downloads
    abstract fun downloadedVideoDao(): DownloadedVideoDao
    abstract fun downloadQueueDao(): DownloadQueueDao

    // Search
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun searchSuggestionDao(): SearchSuggestionDao

    // Note: Parental Control settings/profiles DAOs moved to companion app
    // ScreenTimeLogDao kept for usage analytics
    abstract fun screenTimeLogDao(): ScreenTimeLogDao

    // Usage Analytics
    abstract fun appUsageDao(): AppUsageDao

    // Music
    abstract fun trackDao(): TrackDao
    abstract fun musicPlaylistDao(): MusicPlaylistDao
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun musicListeningHistoryDao(): MusicListeningHistoryDao
}
