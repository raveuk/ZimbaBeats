package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.zimbabeats.core.data.local.entity.music.TrackEntity

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE  // Delete videos when playlist is deleted
        ),
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.NO_ACTION  // Don't cascade - video may be in multiple playlists
        )
    ],
    indices = [Index("playlistId"), Index("videoId")]
)
data class PlaylistVideoEntity(
    val playlistId: Long,
    val videoId: String,
    val position: Int,                        // Order in playlist
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Junction table for unified playlist tracks (music)
 * Allows the same playlists to contain both videos and music tracks
 */
@Entity(
    tableName = "video_playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE  // Delete tracks when playlist is deleted
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.NO_ACTION  // Don't cascade - track may be in multiple playlists
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val position: Int,                        // Order in playlist
    val addedAt: Long = System.currentTimeMillis()
)
