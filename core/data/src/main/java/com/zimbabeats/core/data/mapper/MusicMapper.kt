package com.zimbabeats.core.data.mapper

import com.zimbabeats.core.data.local.entity.music.*
import com.zimbabeats.core.domain.model.music.*

/**
 * Mapper functions for music entities and domain models
 */

// ==================== Track Mappings ====================

fun TrackEntity.toDomain(isFavorite: Boolean = false): Track {
    return Track(
        id = id,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = albumName,
        albumId = albumId,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        isExplicit = isExplicit,
        playCount = playCount,
        addedAt = addedAt,
        isFavorite = isFavorite
    )
}

fun Track.toEntity(): TrackEntity {
    return TrackEntity(
        id = id,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumName = albumName,
        albumId = albumId,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        isExplicit = isExplicit,
        playCount = playCount,
        addedAt = addedAt
    )
}

// ==================== MusicPlaylist Mappings ====================

fun MusicPlaylistEntity.toDomain(tracks: List<Track> = emptyList()): MusicPlaylist {
    return MusicPlaylist(
        id = id,
        name = name,
        description = description,
        thumbnailUrl = thumbnailUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        trackCount = tracks.size,
        totalDuration = tracks.sumOf { it.duration },
        tracks = tracks
    )
}

fun MusicPlaylist.toEntity(): MusicPlaylistEntity {
    return MusicPlaylistEntity(
        id = id,
        name = name,
        description = description,
        thumbnailUrl = thumbnailUrl,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// ==================== Batch Mappings ====================

fun List<TrackEntity>.toDomainList(favoriteIds: Set<String> = emptySet()): List<Track> {
    return map { it.toDomain(isFavorite = favoriteIds.contains(it.id)) }
}

fun List<Track>.toEntityList(): List<TrackEntity> {
    return map { it.toEntity() }
}
