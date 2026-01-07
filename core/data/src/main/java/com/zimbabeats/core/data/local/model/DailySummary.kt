package com.zimbabeats.core.data.local.model

import androidx.room.ColumnInfo

/**
 * Data class for daily screen time summary
 */
data class DailySummary(
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "total")
    val total: Int
)
