package com.zimbabeats.core.domain.filter

import com.zimbabeats.core.domain.model.VideoCategory

enum class AgeGroup(val maxAge: Int, val label: String) {
    UNDER_5(5, "Under 5"),
    UNDER_8(8, "Under 8"),
    UNDER_13(13, "Under 13"),
    UNDER_16(16, "Under 16");

    companion object {
        fun fromAge(age: Int): AgeGroup = when {
            age < 5 -> UNDER_5
            age < 8 -> UNDER_8
            age < 13 -> UNDER_13
            else -> UNDER_16
        }

        fun fromCode(code: String): AgeGroup = when (code) {
            // Legacy mappings for removed age groups
            "UNDER_10" -> UNDER_8
            "UNDER_12" -> UNDER_13
            "UNDER_14" -> UNDER_13
            else -> entries.find { it.name == code } ?: UNDER_16
        }
    }
}

data class AgeConfig(
    val maxDuration: Int,
    val strictMode: Boolean,
    val allowedCategories: Set<VideoCategory>,
    val maxTitleLength: Int
)

data class FilterResult(
    val allowed: Boolean,
    val reason: String? = null,
    val score: Int
)
