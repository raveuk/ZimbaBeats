package com.zimbabeats.core.data.local.entity.music

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a cached music track
 */
@Entity(tableName = "music_tracks")
data class TrackEntity(
    @PrimaryKey val id: String,        // YouTube video ID
    val title: String,
    val artistName: String,
    val artistId: String?,
    val albumName: String?,
    val albumId: String?,
    val thumbnailUrl: String,
    val duration: Long,                 // In milliseconds
    val isExplicit: Boolean = false,
    val playCount: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null
)

/**
 * Entity for user-created music playlists (separate from video playlists)
 */
@Entity(tableName = "music_playlists")
data class MusicPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Junction table for music playlist tracks
 */
@Entity(
    tableName = "music_playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = MusicPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["trackId"])
    ]
)
data class MusicPlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis(),
    val position: Int
)

/**
 * Entity for favorite tracks
 */
@Entity(
    tableName = "favorite_tracks",
    indices = [Index(value = ["addedAt"])]
)
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Entity for music listening history
 */
@Entity(
    tableName = "music_listening_history",
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["listenedAt"])
    ]
)
data class MusicListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val listenedAt: Long = System.currentTimeMillis(),
    val listenDuration: Long  // How long user listened in milliseconds
)
