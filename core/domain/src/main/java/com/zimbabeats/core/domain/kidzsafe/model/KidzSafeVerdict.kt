package com.zimbabeats.core.domain.kidzsafe.model

/**
 * KidzSafe Engine - Content Verdict
 *
 * The final verdict for content filtering decisions.
 * Contains the allow/block decision, Guardian Score, and reasoning.
 */
data class KidzSafeVerdict(
    val allowed: Boolean,
    val score: GuardianScore,
    val rating: KidzSafeRating,
    val trustLevel: TrustLevel,
    val reasons: List<VerdictReason>,
    val evaluatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create an allowed verdict
         */
        fun allowed(
            score: GuardianScore,
            rating: KidzSafeRating,
            trustLevel: TrustLevel,
            reasons: List<VerdictReason> = emptyList()
        ) = KidzSafeVerdict(
            allowed = true,
            score = score,
            rating = rating,
            trustLevel = trustLevel,
            reasons = reasons
        )

        /**
         * Create a blocked verdict
         */
        fun blocked(
            score: GuardianScore,
            rating: KidzSafeRating,
            trustLevel: TrustLevel,
            reasons: List<VerdictReason>
        ) = KidzSafeVerdict(
            allowed = false,
            score = score,
            rating = rating,
            trustLevel = trustLevel,
            reasons = reasons
        )

        /**
         * Create a blocked verdict for trusted channel bypass
         */
        fun trustedChannelApproved(
            score: GuardianScore,
            rating: KidzSafeRating,
            trustLevel: TrustLevel,
            channelName: String
        ) = KidzSafeVerdict(
            allowed = true,
            score = score,
            rating = rating,
            trustLevel = trustLevel,
            reasons = listOf(
                VerdictReason(
                    type = ReasonType.TRUSTED_SOURCE,
                    message = "Approved: Trusted channel '$channelName'",
                    impact = ReasonImpact.POSITIVE
                )
            )
        )

        /**
         * Create a blocked verdict for blocked channel
         */
        fun blockedChannel(
            rating: KidzSafeRating,
            channelName: String
        ) = KidzSafeVerdict(
            allowed = false,
            score = GuardianScore.blocked(),
            rating = rating,
            trustLevel = TrustLevel.BLOCKED,
            reasons = listOf(
                VerdictReason(
                    type = ReasonType.BLOCKED_SOURCE,
                    message = "Blocked: Channel '$channelName' is blocked",
                    impact = ReasonImpact.CRITICAL
                )
            )
        )
    }

    /**
     * Get the primary reason for the verdict
     */
    val primaryReason: VerdictReason?
        get() = reasons.maxByOrNull { it.impact.severity }

    /**
     * Get all blocking reasons
     */
    val blockingReasons: List<VerdictReason>
        get() = reasons.filter { it.impact == ReasonImpact.CRITICAL || it.impact == ReasonImpact.NEGATIVE }

    /**
     * Get human-readable summary
     */
    fun getSummary(): String = buildString {
        if (allowed) {
            append("ALLOWED - ${score.grade.displayName} (${score.total}/1000)")
        } else {
            append("BLOCKED - ${primaryReason?.message ?: "Score too low"}")
        }
    }
}

/**
 * Reason for a verdict decision
 */
data class VerdictReason(
    val type: ReasonType,
    val message: String,
    val impact: ReasonImpact,
    val ruleId: String? = null
)

/**
 * Types of reasons that affect content filtering
 */
enum class ReasonType {
    TRUSTED_SOURCE,
    BLOCKED_SOURCE,
    KEYWORD_MATCH,
    SUSPICIOUS_PATTERN,
    LOW_SCORE,
    HIGH_SCORE,
    CATEGORY_MATCH,
    CATEGORY_MISMATCH,
    DURATION_FIT,
    DURATION_EXCEEDED,
    COMMUNITY_POSITIVE,
    COMMUNITY_NEGATIVE,
    METADATA_QUALITY,
    RULE_TRIGGERED,
    MANUAL_OVERRIDE
}

/**
 * Impact of a reason on the verdict
 */
enum class ReasonImpact(val severity: Int) {
    /**
     * Critical negative - immediate block
     */
    CRITICAL(100),

    /**
     * Negative impact on score
     */
    NEGATIVE(75),

    /**
     * Neutral/informational
     */
    NEUTRAL(50),

    /**
     * Positive impact on score
     */
    POSITIVE(25),

    /**
     * Guaranteed approval (trusted source)
     */
    APPROVED(0)
}

/**
 * Content item to be evaluated
 */
data class ContentItem(
    val id: String,
    val title: String,
    val description: String?,
    val channelId: String,
    val channelName: String,
    val duration: Long,
    val viewCount: Long,
    val likeCount: Long? = null,
    val dislikeCount: Long? = null,
    val category: String? = null,
    val thumbnailUrl: String? = null,
    val publishedAt: Long? = null
) {
    /**
     * Get combined text content for analysis
     */
    val textContent: String
        get() = buildString {
            append(title.lowercase())
            append(" ")
            append(channelName.lowercase())
            append(" ")
            description?.let { append(it.lowercase()) }
        }

    /**
     * Get like ratio (0.0 to 1.0)
     */
    val likeRatio: Float?
        get() {
            val likes = likeCount ?: return null
            val dislikes = dislikeCount ?: 0
            val total = likes + dislikes
            return if (total > 0) likes.toFloat() / total else null
        }
}
