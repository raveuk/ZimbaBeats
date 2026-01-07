package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parental_settings")
data class ParentalSettingsEntity(
    @PrimaryKey val id: Long = 1,            // Singleton
    val isEnabled: Boolean = false,
    val pin: String? = null,                  // PIN hash for security
    val selectedAgeLevel: String = "ALL",     // Selected age rating level
    val maxScreenTimeMinutes: Int = 60,       // Daily screen time limit
    val blockedChannels: String = "",         // Comma-separated channel IDs
    val blockedKeywords: String = "",         // Comma-separated keywords
    val requirePinForSettings: Boolean = true,
    val requirePinForDownloads: Boolean = false,
    val allowSearch: Boolean = true,
    val bedtimeStart: String? = null,         // HH:mm format
    val bedtimeEnd: String? = null,           // HH:mm format
    val updatedAt: Long = System.currentTimeMillis()
)
