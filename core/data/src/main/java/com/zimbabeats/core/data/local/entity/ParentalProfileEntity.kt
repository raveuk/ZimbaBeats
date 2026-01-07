package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parental_profiles")
data class ParentalProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                         // e.g., "Mom's Profile", "Dad's Profile"
    val pin: String,                          // Hashed PIN
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
