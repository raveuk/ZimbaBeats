package com.zimbabeats.core.data.mapper

import com.zimbabeats.core.data.local.entity.PlaylistEntity
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.PlaylistColor
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Track

fun PlaylistEntity.toDomain(
    videos: List<Video> = emptyList(),
    tracks: List<Track> = emptyList()
): Playlist = Playlist(
    id = id,
    name = name,
    description = description,
    thumbnailUrl = thumbnailUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    videoCount = videoCount,
    trackCount = trackCount,
    isFavorite = isFavorite,
    color = color.toPlaylistColor(),
    videos = videos,
    tracks = tracks
)

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    description = description,
    thumbnailUrl = thumbnailUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    videoCount = videoCount,
    trackCount = trackCount,
    isFavorite = isFavorite,
    color = color.hex
)

fun String.toPlaylistColor(): PlaylistColor = PlaylistColor.values().find { it.hex == this } ?: PlaylistColor.PINK
