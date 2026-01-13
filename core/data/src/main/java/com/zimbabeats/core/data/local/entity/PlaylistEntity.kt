package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_playlists")
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
    val color: String = "#FF6B9D",            // Kid-friendly color
    // Sharing fields
    val shareCode: String? = null,            // 6-char share code if shared
    val sharedAt: Long? = null,               // When share code was generated
    val isImported: Boolean = false,          // True if imported from another kid
    val importedFrom: String? = null,         // Name of kid who shared it
    val importedAt: Long? = null              // When it was imported
)
