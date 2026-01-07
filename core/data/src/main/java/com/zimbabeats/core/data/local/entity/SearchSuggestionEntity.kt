package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_suggestions")
data class SearchSuggestionEntity(
    @PrimaryKey val suggestion: String,
    val frequency: Int = 1,                   // How many times suggested
    val lastUsed: Long = System.currentTimeMillis()
)
