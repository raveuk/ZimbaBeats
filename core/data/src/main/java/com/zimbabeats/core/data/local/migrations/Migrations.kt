package com.zimbabeats.core.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 7 to 8: Added playlist sharing fields
 * Version 8 added: shareCode, sharedAt, isImported, importedFrom, importedAt
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE playlists ADD COLUMN shareCode TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE playlists ADD COLUMN sharedAt INTEGER DEFAULT NULL")
        database.execSQL("ALTER TABLE playlists ADD COLUMN isImported INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE playlists ADD COLUMN importedFrom TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE playlists ADD COLUMN importedAt INTEGER DEFAULT NULL")
    }
}

/**
 * Migration from version 8 to 9: Renamed tables for consistency
 * - "playlists" -> "video_playlists"
 * - Added trackCount column to playlists
 * - Added video_playlist_tracks table for music in unified playlists
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Rename playlists table to video_playlists
        database.execSQL("ALTER TABLE playlists RENAME TO video_playlists")

        // Add trackCount column
        database.execSQL("ALTER TABLE video_playlists ADD COLUMN trackCount INTEGER NOT NULL DEFAULT 0")

        // Create video_playlist_tracks table for music tracks in unified playlists
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS video_playlist_tracks (
                playlistId INTEGER NOT NULL,
                trackId TEXT NOT NULL,
                position INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(playlistId, trackId),
                FOREIGN KEY(playlistId) REFERENCES video_playlists(id) ON DELETE CASCADE,
                FOREIGN KEY(trackId) REFERENCES music_tracks(id) ON DELETE CASCADE
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS index_video_playlist_tracks_playlistId ON video_playlist_tracks(playlistId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_video_playlist_tracks_trackId ON video_playlist_tracks(trackId)")
    }
}

/**
 * Migration from version 9 to 10: Fixed foreign key constraints
 * Changed onDelete from CASCADE to NO_ACTION for video/track references
 * (Videos/tracks may be in multiple playlists, shouldn't cascade delete)
 *
 * Note: SQLite doesn't support ALTER TABLE to modify foreign keys,
 * so we recreate the tables with the correct constraints
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Recreate playlist_videos with corrected foreign key (NO_ACTION for videoId)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS playlist_videos_new (
                playlistId INTEGER NOT NULL,
                videoId TEXT NOT NULL,
                position INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(playlistId, videoId),
                FOREIGN KEY(playlistId) REFERENCES video_playlists(id) ON DELETE CASCADE,
                FOREIGN KEY(videoId) REFERENCES videos(id) ON DELETE NO ACTION
            )
        """.trimIndent())

        // Copy data from old table
        database.execSQL("""
            INSERT OR IGNORE INTO playlist_videos_new (playlistId, videoId, position, addedAt)
            SELECT playlistId, videoId, position, addedAt FROM playlist_videos
        """.trimIndent())

        // Drop old table and rename new one
        database.execSQL("DROP TABLE IF EXISTS playlist_videos")
        database.execSQL("ALTER TABLE playlist_videos_new RENAME TO playlist_videos")

        // Recreate indices
        database.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_videos_playlistId ON playlist_videos(playlistId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_videos_videoId ON playlist_videos(videoId)")

        // Recreate video_playlist_tracks with corrected foreign key (NO_ACTION for trackId)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS video_playlist_tracks_new (
                playlistId INTEGER NOT NULL,
                trackId TEXT NOT NULL,
                position INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(playlistId, trackId),
                FOREIGN KEY(playlistId) REFERENCES video_playlists(id) ON DELETE CASCADE,
                FOREIGN KEY(trackId) REFERENCES music_tracks(id) ON DELETE NO ACTION
            )
        """.trimIndent())

        // Copy data from old table
        database.execSQL("""
            INSERT OR IGNORE INTO video_playlist_tracks_new (playlistId, trackId, position, addedAt)
            SELECT playlistId, trackId, position, addedAt FROM video_playlist_tracks
        """.trimIndent())

        // Drop old table and rename new one
        database.execSQL("DROP TABLE IF EXISTS video_playlist_tracks")
        database.execSQL("ALTER TABLE video_playlist_tracks_new RENAME TO video_playlist_tracks")

        // Recreate indices
        database.execSQL("CREATE INDEX IF NOT EXISTS index_video_playlist_tracks_playlistId ON video_playlist_tracks(playlistId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_video_playlist_tracks_trackId ON video_playlist_tracks(trackId)")
    }
}

/**
 * Migration from version 10 to 11: Denormalized favorite_videos table
 *
 * Previously favorite_videos only stored videoId with a foreign key to videos table.
 * This caused favorites to be deleted when the video cache was cleared (on app startup).
 *
 * New structure stores full video metadata in favorite_videos, making favorites
 * independent of the video cache.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create new denormalized favorite_videos table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS favorite_videos_new (
                videoId TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                thumbnailUrl TEXT NOT NULL,
                channelName TEXT NOT NULL,
                channelId TEXT NOT NULL,
                duration INTEGER NOT NULL,
                viewCount INTEGER NOT NULL,
                publishedAt INTEGER NOT NULL,
                isKidFriendly INTEGER NOT NULL DEFAULT 1,
                ageRating TEXT NOT NULL DEFAULT 'ALL',
                category TEXT,
                addedAt INTEGER NOT NULL
            )
        """.trimIndent())

        // Migrate existing favorites by joining with videos table
        // Only favorites where the video still exists will be migrated
        database.execSQL("""
            INSERT OR IGNORE INTO favorite_videos_new (
                videoId, title, description, thumbnailUrl, channelName, channelId,
                duration, viewCount, publishedAt, isKidFriendly, ageRating, category, addedAt
            )
            SELECT
                fv.videoId,
                v.title,
                v.description,
                v.thumbnailUrl,
                v.channelName,
                v.channelId,
                v.duration,
                v.viewCount,
                v.publishedAt,
                v.isKidFriendly,
                v.ageRating,
                v.category,
                fv.addedAt
            FROM favorite_videos fv
            INNER JOIN videos v ON fv.videoId = v.id
        """.trimIndent())

        // Drop old table and rename new one
        database.execSQL("DROP TABLE IF EXISTS favorite_videos")
        database.execSQL("ALTER TABLE favorite_videos_new RENAME TO favorite_videos")

        // Create index on videoId
        database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_videos_videoId ON favorite_videos(videoId)")
    }
}
