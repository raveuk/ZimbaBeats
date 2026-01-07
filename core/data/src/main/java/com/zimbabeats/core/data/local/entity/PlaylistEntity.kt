package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,                // First item's thumbnail
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val videoCount: Int = 0,
    val trackCount: Int = 0,                  // Music track count
    val isFavorite: Boolean = false,
    val color: String = "#FF6B9D"             // Kid-friendly color
)
