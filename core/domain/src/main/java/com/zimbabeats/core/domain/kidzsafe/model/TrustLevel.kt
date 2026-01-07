package com.zimbabeats.core.domain.kidzsafe.model

/**
 * KidzSafe Engine - Trust Level System
 *
 * Trust levels categorize content sources based on their reliability
 * for providing kid-safe content. Higher trust levels receive score
 * multipliers that boost their Guardian Score.
 */
enum class TrustLevel(
    val displayName: String,
    val multiplier: Float,
    val baseScore: Int,
    val description: String
) {
    /**
     * Verified Partner - Premium kid content creators
     * Examples: CoComelon, Pinkfong, Super Simple Songs
     */
    VERIFIED_PARTNER(
        displayName = "Verified Partner",
        multiplier = 1.0f,
        baseScore = 250,
        description = "Premium verified kid content creators"
    ),

    /**
     * Trusted - Established family-friendly channels
     * Examples: PBS Kids, Disney Junior, Nick Jr
     */
    TRUSTED(
        displayName = "Trusted",
        multiplier = 0.9f,
        baseScore = 225,
        description = "Established family-friendly channels"
    ),

    /**
     * Recognized - Channels with positive reputation
     */
    RECOGNIZED(
        displayName = "Recognized",
        multiplier = 0.75f,
        baseScore = 188,
        description = "Channels with positive reputation"
    ),

    /**
     * Neutral - Unknown channels with no reputation data
     */
    NEUTRAL(
        displayName = "Neutral",
        multiplier = 0.5f,
        baseScore = 125,
        description = "Unknown channels requiring content analysis"
    ),

    /**
     * Suspicious - Channels with flagged content
     */
    SUSPICIOUS(
        displayName = "Suspicious",
        multiplier = 0.2f,
        baseScore = 50,
        description = "Channels with flagged or questionable content"
    ),

    /**
     * Blocked - User or system blocked channels
     */
    BLOCKED(
        displayName = "Blocked",
        multiplier = 0.0f,
        baseScore = 0,
        description = "Blocked channels - content never shown"
    );

    companion object {
        /**
         * Get trust level from a reputation score (0-100)
         */
        fun fromReputationScore(score: Int): TrustLevel = when {
            score >= 95 -> VERIFIED_PARTNER
            score >= 85 -> TRUSTED
            score >= 70 -> RECOGNIZED
            score >= 40 -> NEUTRAL
            score >= 10 -> SUSPICIOUS
            else -> BLOCKED
        }

        /**
         * Default trust level for unknown sources
         */
        val DEFAULT = NEUTRAL
    }

    /**
     * Check if this trust level should auto-approve content
     */
    val autoApprove: Boolean
        get() = this == VERIFIED_PARTNER || this == TRUSTED

    /**
     * Check if this trust level requires extra content analysis
     */
    val requiresAnalysis: Boolean
        get() = this == NEUTRAL || this == SUSPICIOUS

    /**
     * Check if this trust level blocks all content
     */
    val isBlocked: Boolean
        get() = this == BLOCKED

    /**
     * Apply multiplier to a base score
     */
    fun applyMultiplier(score: Int): Int =
        (score * multiplier).toInt()
}

/**
 * Trust Verification Result
 */
data class TrustVerification(
    val channelId: String,
    val channelName: String,
    val trustLevel: TrustLevel,
    val reasons: List<String>,
    val verifiedAt: Long = System.currentTimeMillis()
) {
    /**
     * Get the trust score contribution for Guardian Score
     */
    val trustScore: Int
        get() = trustLevel.baseScore
}
