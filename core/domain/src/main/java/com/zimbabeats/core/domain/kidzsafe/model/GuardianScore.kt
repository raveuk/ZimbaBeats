package com.zimbabeats.core.domain.kidzsafe.model

/**
 * KidzSafe Engine - Guardian Score System
 *
 * The Guardian Score is a 0-1000 point weighted algorithm that evaluates
 * content safety across multiple factors. Each content item receives a
 * score and corresponding grade.
 */
data class GuardianScore(
    val total: Int,
    val breakdown: ScoreBreakdown,
    val grade: GuardianGrade = GuardianGrade.fromScore(total)
) {
    init {
        require(total in 0..1000) { "Guardian Score must be between 0 and 1000" }
    }

    companion object {
        /**
         * Maximum possible score
         */
        const val MAX_SCORE = 1000

        /**
         * Create a score from weighted components
         */
        fun calculate(
            trustScore: Int,
            contentScore: Int,
            categoryScore: Int,
            durationScore: Int,
            communityScore: Int,
            metadataScore: Int
        ): GuardianScore {
            val breakdown = ScoreBreakdown(
                trustScore = trustScore,
                contentScore = contentScore,
                categoryScore = categoryScore,
                durationScore = durationScore,
                communityScore = communityScore,
                metadataScore = metadataScore
            )
            return GuardianScore(
                total = breakdown.calculateTotal(),
                breakdown = breakdown
            )
        }

        /**
         * Create a blocked/zero score
         */
        fun blocked(reason: String = "Content blocked"): GuardianScore = GuardianScore(
            total = 0,
            breakdown = ScoreBreakdown.zero()
        )
    }

    /**
     * Check if this score meets the threshold for a rating
     */
    fun meetsThreshold(rating: KidzSafeRating): Boolean =
        total >= rating.requiredScore

    /**
     * Check if content should be allowed for given rating
     */
    fun isAllowedFor(rating: KidzSafeRating): Boolean =
        rating == KidzSafeRating.ALL || meetsThreshold(rating)
}

/**
 * Guardian Score Grade - Letter grades based on score ranges
 */
enum class GuardianGrade(
    val displayName: String,
    val minScore: Int,
    val maxScore: Int,
    val colorHex: String
) {
    PLATINUM_SAFE("Platinum Safe", 900, 1000, "#E5E4E2"),
    GOLD_SAFE("Gold Safe", 800, 899, "#FFD700"),
    SILVER_SAFE("Silver Safe", 700, 799, "#C0C0C0"),
    BRONZE_SAFE("Bronze Safe", 600, 699, "#CD7F32"),
    CAUTION("Caution", 400, 599, "#FFA500"),
    RESTRICTED("Restricted", 0, 399, "#FF4444");

    companion object {
        fun fromScore(score: Int): GuardianGrade = when {
            score >= 900 -> PLATINUM_SAFE
            score >= 800 -> GOLD_SAFE
            score >= 700 -> SILVER_SAFE
            score >= 600 -> BRONZE_SAFE
            score >= 400 -> CAUTION
            else -> RESTRICTED
        }
    }

    /**
     * Check if this grade is safe enough for a rating
     */
    fun isSafeFor(rating: KidzSafeRating): Boolean =
        minScore >= rating.requiredScore
}

/**
 * Score Breakdown - Individual component scores
 *
 * Weights:
 * - Trust Level: 25% (250 points max)
 * - Content Signals: 30% (300 points max)
 * - Category Match: 15% (150 points max)
 * - Duration Fit: 10% (100 points max)
 * - Community Signals: 10% (100 points max)
 * - Metadata Quality: 10% (100 points max)
 */
data class ScoreBreakdown(
    val trustScore: Int,      // 0-250 (25%)
    val contentScore: Int,    // 0-300 (30%)
    val categoryScore: Int,   // 0-150 (15%)
    val durationScore: Int,   // 0-100 (10%)
    val communityScore: Int,  // 0-100 (10%)
    val metadataScore: Int    // 0-100 (10%)
) {
    companion object {
        const val TRUST_WEIGHT = 0.25f
        const val CONTENT_WEIGHT = 0.30f
        const val CATEGORY_WEIGHT = 0.15f
        const val DURATION_WEIGHT = 0.10f
        const val COMMUNITY_WEIGHT = 0.10f
        const val METADATA_WEIGHT = 0.10f

        const val MAX_TRUST = 250
        const val MAX_CONTENT = 300
        const val MAX_CATEGORY = 150
        const val MAX_DURATION = 100
        const val MAX_COMMUNITY = 100
        const val MAX_METADATA = 100

        fun zero() = ScoreBreakdown(0, 0, 0, 0, 0, 0)
    }

    /**
     * Calculate total score from components
     */
    fun calculateTotal(): Int = (
        trustScore.coerceIn(0, MAX_TRUST) +
        contentScore.coerceIn(0, MAX_CONTENT) +
        categoryScore.coerceIn(0, MAX_CATEGORY) +
        durationScore.coerceIn(0, MAX_DURATION) +
        communityScore.coerceIn(0, MAX_COMMUNITY) +
        metadataScore.coerceIn(0, MAX_METADATA)
    ).coerceIn(0, GuardianScore.MAX_SCORE)

    /**
     * Get percentage of each component filled
     */
    fun getPercentages(): Map<String, Float> = mapOf(
        "Trust" to (trustScore.toFloat() / MAX_TRUST * 100),
        "Content" to (contentScore.toFloat() / MAX_CONTENT * 100),
        "Category" to (categoryScore.toFloat() / MAX_CATEGORY * 100),
        "Duration" to (durationScore.toFloat() / MAX_DURATION * 100),
        "Community" to (communityScore.toFloat() / MAX_COMMUNITY * 100),
        "Metadata" to (metadataScore.toFloat() / MAX_METADATA * 100)
    )
}
