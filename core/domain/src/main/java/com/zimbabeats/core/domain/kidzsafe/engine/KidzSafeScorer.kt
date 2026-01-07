package com.zimbabeats.core.domain.kidzsafe.engine

import com.zimbabeats.core.domain.kidzsafe.model.ContentItem
import com.zimbabeats.core.domain.kidzsafe.model.GuardianScore
import com.zimbabeats.core.domain.kidzsafe.model.KidzSafeRating
import com.zimbabeats.core.domain.kidzsafe.model.ScoreBreakdown
import com.zimbabeats.core.domain.kidzsafe.model.TrustLevel

/**
 * KidzSafe Engine - Guardian Score Calculator
 *
 * Calculates the Guardian Score (0-1000) using a weighted multi-factor algorithm.
 *
 * Score Components:
 * - Trust Level: 25% (250 points max) - Channel/creator trust verification
 * - Content Signals: 30% (300 points max) - Keyword/pattern analysis
 * - Category Match: 15% (150 points max) - Age-appropriate category
 * - Duration Fit: 10% (100 points max) - Age-appropriate length
 * - Community Signals: 10% (100 points max) - Like ratio, engagement
 * - Metadata Quality: 10% (100 points max) - Title/description analysis
 */
class KidzSafeScorer {

    /**
     * Calculate Guardian Score for content
     */
    fun calculateScore(
        content: ContentItem,
        rating: KidzSafeRating,
        trustLevel: TrustLevel
    ): GuardianScore {
        // Get individual component scores
        val trustScore = calculateTrustScore(trustLevel)
        val contentScore = calculateContentScore(content, rating)
        val categoryScore = calculateCategoryScore(content, rating)
        val durationScore = calculateDurationScore(content, rating)
        val communityScore = calculateCommunityScore(content)
        val metadataScore = calculateMetadataScore(content)

        return GuardianScore.calculate(
            trustScore = trustScore,
            contentScore = contentScore,
            categoryScore = categoryScore,
            durationScore = durationScore,
            communityScore = communityScore,
            metadataScore = metadataScore
        )
    }

    /**
     * Calculate trust score (0-250 points)
     * Based on channel trust level
     */
    private fun calculateTrustScore(trustLevel: TrustLevel): Int {
        return trustLevel.baseScore
    }

    /**
     * Calculate content score (0-300 points)
     * Based on keyword analysis of title, description, and channel name
     */
    private fun calculateContentScore(content: ContentItem, rating: KidzSafeRating): Int {
        var score = 150 // Start at neutral (50%)

        val text = content.textContent

        // Boost for safe keywords
        val safeKeywordCount = SAFE_KEYWORDS.count { keyword ->
            text.contains(keyword)
        }
        score += (safeKeywordCount * 15).coerceAtMost(100)

        // Boost for strong safe indicators
        if (STRONG_SAFE_INDICATORS.any { text.contains(it) }) {
            score += 50
        }

        // Penalty for suspicious patterns (if any slip through rules)
        val suspiciousCount = SUSPICIOUS_PATTERNS.count { pattern ->
            text.contains(pattern)
        }
        if (suspiciousCount > 0 && rating.strictness >= 4) {
            score -= (suspiciousCount * 30).coerceAtMost(100)
        }

        return score.coerceIn(0, ScoreBreakdown.MAX_CONTENT)
    }

    /**
     * Calculate category score (0-150 points)
     * Based on category match for age group
     */
    private fun calculateCategoryScore(content: ContentItem, rating: KidzSafeRating): Int {
        val category = content.category?.lowercase() ?: return 75 // Neutral if no category

        // Age-appropriate categories
        val appropriateCategories = when {
            rating.strictness >= 4 -> setOf(
                "education", "entertainment", "animation", "music", "kids", "family"
            )
            rating.strictness >= 2 -> setOf(
                "education", "entertainment", "animation", "music", "kids", "family",
                "gaming", "science", "technology", "howto", "sports"
            )
            else -> emptySet() // All categories allowed
        }

        return when {
            appropriateCategories.isEmpty() -> 150 // All allowed
            category in appropriateCategories -> 150
            else -> 50 // Penalty for non-standard category
        }
    }

    /**
     * Calculate duration score (0-100 points)
     * Younger kids should have shorter content
     */
    private fun calculateDurationScore(content: ContentItem, rating: KidzSafeRating): Int {
        // Duration in seconds
        val durationSeconds = if (content.duration > 100000) {
            content.duration / 1000
        } else {
            content.duration
        }

        // Max recommended duration by rating
        val maxDuration = when (rating) {
            KidzSafeRating.UNDER_5 -> 600      // 10 minutes
            KidzSafeRating.UNDER_8 -> 900      // 15 minutes
            KidzSafeRating.UNDER_10 -> 1200    // 20 minutes
            KidzSafeRating.UNDER_12 -> 1800    // 30 minutes
            KidzSafeRating.UNDER_13 -> 2100    // 35 minutes
            KidzSafeRating.UNDER_14 -> 2400    // 40 minutes
            KidzSafeRating.UNDER_16 -> 3600    // 60 minutes
            KidzSafeRating.ALL -> Long.MAX_VALUE
        }

        return when {
            rating == KidzSafeRating.ALL -> 100
            durationSeconds <= maxDuration * 0.5 -> 100  // Under half max
            durationSeconds <= maxDuration * 0.75 -> 80  // Under 3/4 max
            durationSeconds <= maxDuration -> 60         // Under max
            durationSeconds <= maxDuration * 1.5 -> 30   // Slightly over
            else -> 10                                    // Way over
        }
    }

    /**
     * Calculate community score (0-100 points)
     * Based on like ratio and engagement
     */
    private fun calculateCommunityScore(content: ContentItem): Int {
        // Default if no community data
        val likeRatio = content.likeRatio ?: return 50

        return when {
            likeRatio >= 0.98 -> 100
            likeRatio >= 0.95 -> 90
            likeRatio >= 0.90 -> 80
            likeRatio >= 0.80 -> 70
            likeRatio >= 0.70 -> 50
            likeRatio >= 0.50 -> 30
            else -> 10
        }
    }

    /**
     * Calculate metadata quality score (0-100 points)
     * Based on title length, description presence, etc.
     */
    private fun calculateMetadataScore(content: ContentItem): Int {
        var score = 50 // Start at neutral

        // Title quality
        val titleLength = content.title.length
        when {
            titleLength in 10..100 -> score += 20  // Good length
            titleLength in 5..150 -> score += 10   // Acceptable
            else -> score -= 10                     // Too short or too long
        }

        // Description presence
        if (!content.description.isNullOrBlank()) {
            score += 15
            if (content.description.length >= 100) {
                score += 10 // Detailed description
            }
        }

        // Channel name quality (not generic)
        if (content.channelName.length >= 3 && !content.channelName.contains("user")) {
            score += 5
        }

        return score.coerceIn(0, ScoreBreakdown.MAX_METADATA)
    }

    companion object {
        /**
         * Safe keywords that boost content score
         */
        val SAFE_KEYWORDS = setOf(
            "kids", "kid", "children", "child", "toddler", "baby", "babies",
            "preschool", "kindergarten", "elementary",
            "nursery", "rhymes", "lullaby", "lullabies",
            "cartoon", "cartoons", "animation", "animated",
            "educational", "learning", "learn", "teach",
            "alphabet", "abc", "numbers", "counting", "123",
            "colors", "shapes", "phonics",
            "sing along", "singalong", "songs for kids",
            "disney", "pixar", "nickelodeon", "nick jr", "pbs kids",
            "playtime", "storytime", "bedtime story", "read aloud",
            "crafts", "diy for kids", "art for kids", "science for kids",
            "family friendly", "kid friendly", "child friendly", "safe for kids"
        )

        /**
         * Strong safe indicators
         */
        val STRONG_SAFE_INDICATORS = setOf(
            "cocomelon", "pinkfong", "super simple", "little baby bum",
            "sesame street", "pbs kids", "nick jr", "disney junior",
            "numberblocks", "alphablocks", "bluey", "peppa pig",
            "paw patrol", "blippi", "ms rachel", "hey bear sensory",
            "nursery rhymes", "abc song", "alphabet song"
        )

        /**
         * Suspicious patterns
         */
        val SUSPICIOUS_PATTERNS = setOf(
            "surprise egg", "wrong heads", "bad baby",
            "finger family", "johny johny", "johnny johnny",
            "prank", "mukbang"
        )
    }
}
