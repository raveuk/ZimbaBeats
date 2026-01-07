package com.zimbabeats.core.domain.kidzsafe.model

/**
 * KidzSafe Engine - Age Rating System
 *
 * The core age-based rating system for the KidzSafe Engine.
 * Each rating tier has specific strictness levels and required Guardian Scores.
 */
enum class KidzSafeRating(
    val displayName: String,
    val ageLimit: Int,
    val strictness: Int,
    val requiredScore: Int,
    val description: String
) {
    /**
     * All Ages - No restrictions, all content available
     */
    ALL(
        displayName = "All Ages",
        ageLimit = 0,
        strictness = 0,
        requiredScore = 0,
        description = "No restrictions - all content available"
    ),

    /**
     * Kids Under 5 - Strictest filtering for toddlers and preschoolers
     * Requires Gold Safe or higher (600+ Guardian Score)
     */
    UNDER_5(
        displayName = "Kids Under 5",
        ageLimit = 5,
        strictness = 6,
        requiredScore = 600,
        description = "Strictest filtering for toddlers and preschoolers"
    ),

    /**
     * Kids Under 8 - Very strict filtering for young children
     */
    UNDER_8(
        displayName = "Kids Under 8",
        ageLimit = 8,
        strictness = 5,
        requiredScore = 575,
        description = "Very strict filtering for young children"
    ),

    /**
     * Kids Under 10 - Strict filtering for elementary school children
     */
    UNDER_10(
        displayName = "Kids Under 10",
        ageLimit = 10,
        strictness = 4,
        requiredScore = 550,
        description = "Strict filtering for elementary school children"
    ),

    /**
     * Kids Under 12 - Moderate filtering for pre-teens
     */
    UNDER_12(
        displayName = "Kids Under 12",
        ageLimit = 12,
        strictness = 3,
        requiredScore = 500,
        description = "Moderate filtering for pre-teens"
    ),

    /**
     * Kids Under 13 - Moderate-light filtering for pre-teens
     */
    UNDER_13(
        displayName = "Kids Under 13",
        ageLimit = 13,
        strictness = 2,
        requiredScore = 475,
        description = "Moderate-light filtering for older pre-teens"
    ),

    /**
     * Kids Under 14 - Light filtering for young teens
     */
    UNDER_14(
        displayName = "Kids Under 14",
        ageLimit = 14,
        strictness = 2,
        requiredScore = 450,
        description = "Light filtering for young teens"
    ),

    /**
     * Kids Under 16 - Minimal filtering for older teens
     */
    UNDER_16(
        displayName = "Kids Under 16",
        ageLimit = 16,
        strictness = 1,
        requiredScore = 400,
        description = "Minimal filtering for older teens"
    );

    companion object {
        /**
         * Get the appropriate rating for a given age
         */
        fun forAge(age: Int): KidzSafeRating = when {
            age < 5 -> UNDER_5
            age < 8 -> UNDER_8
            age < 10 -> UNDER_10
            age < 12 -> UNDER_12
            age < 13 -> UNDER_13
            age < 14 -> UNDER_14
            age < 16 -> UNDER_16
            else -> ALL
        }

        /**
         * Get rating by age limit value
         */
        fun fromAgeLimit(ageLimit: Int): KidzSafeRating =
            entries.find { it.ageLimit == ageLimit } ?: ALL
    }

    /**
     * Check if this rating is more restrictive than another
     */
    fun isMoreRestrictiveThan(other: KidzSafeRating): Boolean =
        this.strictness > other.strictness

    /**
     * Check if content with given score passes this rating's threshold
     */
    fun isScoreAcceptable(score: Int): Boolean =
        score >= requiredScore
}
