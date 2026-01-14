package com.zimbabeats.core.domain.kidzsafe.engine

import com.zimbabeats.core.domain.kidzsafe.model.ContentItem
import com.zimbabeats.core.domain.kidzsafe.model.GuardianScore
import com.zimbabeats.core.domain.kidzsafe.model.KidzSafeRating
import com.zimbabeats.core.domain.kidzsafe.model.KidzSafeVerdict
import com.zimbabeats.core.domain.kidzsafe.model.ReasonImpact
import com.zimbabeats.core.domain.kidzsafe.model.ReasonType
import com.zimbabeats.core.domain.kidzsafe.model.TrustLevel
import com.zimbabeats.core.domain.kidzsafe.model.VerdictReason
import com.zimbabeats.core.domain.kidzsafe.rules.KeywordBlocklistRule
import com.zimbabeats.core.domain.kidzsafe.rules.KidzSafeRule
import com.zimbabeats.core.domain.kidzsafe.rules.RuleAction
import com.zimbabeats.core.domain.kidzsafe.rules.RuleContext
import com.zimbabeats.core.domain.kidzsafe.rules.SuspiciousPatternRule
import com.zimbabeats.core.domain.kidzsafe.rules.TrustedChannelRule
import com.zimbabeats.core.domain.model.AgeRating
import com.zimbabeats.core.domain.model.Video

/**
 * KidzSafe Engine - Main Facade
 *
 * The core content filtering engine for Marielikay Beats.
 * Provides a unified interface for evaluating content safety.
 *
 * Features:
 * - Guardian Score algorithm (0-1000 weighted scoring)
 * - Trust Level verification
 * - Extensible rule engine
 * - Age-based content filtering
 */
class KidzSafeEngine(
    private val scorer: KidzSafeScorer = KidzSafeScorer(),
    private val rules: List<KidzSafeRule> = defaultRules()
) {
    companion object {
        /**
         * Engine version for tracking
         */
        const val VERSION = "1.0.0"

        /**
         * Engine name
         */
        const val ENGINE_NAME = "KidzSafe Engine"

        /**
         * Default rules in priority order
         */
        fun defaultRules(): List<KidzSafeRule> = listOf(
            TrustedChannelRule(),
            KeywordBlocklistRule(),
            SuspiciousPatternRule()
        ).sortedByDescending { it.priority }
    }

    // Sorted rules by priority (highest first)
    private val sortedRules = rules.sortedByDescending { it.priority }

    /**
     * Evaluate content and return a verdict
     */
    suspend fun evaluate(content: ContentItem, rating: KidzSafeRating): KidzSafeVerdict {
        // Determine trust level
        val trustLevel = determineTrustLevel(content)

        // Check for blocked channel
        if (trustLevel.isBlocked) {
            return KidzSafeVerdict.blockedChannel(rating, content.channelName)
        }

        // Run through rules
        val ruleContext = RuleContext(
            rating = rating,
            trustLevel = trustLevel,
            strictMode = rating.strictness >= 4
        )

        val reasons = mutableListOf<VerdictReason>()
        var scoreAdjustment = 0

        for (rule in sortedRules) {
            val result = rule.evaluate(content, ruleContext)

            when (result.action) {
                RuleAction.APPROVE -> {
                    // Immediately approve
                    val score = scorer.calculateScore(content, rating, trustLevel)
                    reasons.add(
                        VerdictReason(
                            type = ReasonType.TRUSTED_SOURCE,
                            message = result.reason ?: "Approved by ${rule.name}",
                            impact = ReasonImpact.APPROVED,
                            ruleId = rule.id
                        )
                    )
                    return KidzSafeVerdict.allowed(score, rating, trustLevel, reasons)
                }

                RuleAction.BLOCK -> {
                    // Immediately block
                    reasons.add(
                        VerdictReason(
                            type = ReasonType.RULE_TRIGGERED,
                            message = result.reason ?: "Blocked by ${rule.name}",
                            impact = ReasonImpact.CRITICAL,
                            ruleId = rule.id
                        )
                    )
                    return KidzSafeVerdict.blocked(
                        score = GuardianScore.blocked(),
                        rating = rating,
                        trustLevel = trustLevel,
                        reasons = reasons
                    )
                }

                RuleAction.CONTINUE -> {
                    // Apply score adjustment and continue
                    if (result.scoreAdjustment != 0) {
                        scoreAdjustment += result.scoreAdjustment
                        result.reason?.let { reason ->
                            reasons.add(
                                VerdictReason(
                                    type = if (result.scoreAdjustment > 0) ReasonType.HIGH_SCORE else ReasonType.LOW_SCORE,
                                    message = reason,
                                    impact = if (result.scoreAdjustment > 0) ReasonImpact.POSITIVE else ReasonImpact.NEGATIVE,
                                    ruleId = rule.id
                                )
                            )
                        }
                    }
                }
            }
        }

        // Calculate final score with adjustments
        var score = scorer.calculateScore(content, rating, trustLevel)

        // Apply rule adjustments
        if (scoreAdjustment != 0) {
            val adjustedTotal = (score.total + scoreAdjustment).coerceIn(0, GuardianScore.MAX_SCORE)
            score = GuardianScore(
                total = adjustedTotal,
                breakdown = score.breakdown
            )
        }

        // Determine if content passes threshold
        val allowed = score.isAllowedFor(rating)

        if (!allowed) {
            reasons.add(
                VerdictReason(
                    type = ReasonType.LOW_SCORE,
                    message = "Score ${score.total} below threshold ${rating.requiredScore} for ${rating.displayName}",
                    impact = ReasonImpact.NEGATIVE
                )
            )
        }

        return KidzSafeVerdict(
            allowed = allowed,
            score = score,
            rating = rating,
            trustLevel = trustLevel,
            reasons = reasons
        )
    }

    /**
     * Evaluate a Video using the engine
     */
    suspend fun evaluateVideo(video: Video, ageRating: AgeRating): KidzSafeVerdict {
        val content = video.toContentItem()
        val rating = ageRating.toKidzSafeRating()
        return evaluate(content, rating)
    }

    /**
     * Filter a list of videos
     */
    suspend fun filterVideos(videos: List<Video>, ageRating: AgeRating): List<Video> {
        val rating = ageRating.toKidzSafeRating()
        return videos.filter { video ->
            val verdict = evaluate(video.toContentItem(), rating)
            verdict.allowed
        }
    }

    /**
     * Filter and sort videos by Guardian Score
     */
    suspend fun filterAndSortVideos(videos: List<Video>, ageRating: AgeRating): List<Pair<Video, KidzSafeVerdict>> {
        val rating = ageRating.toKidzSafeRating()
        return videos
            .map { video -> video to evaluate(video.toContentItem(), rating) }
            .filter { (_, verdict) -> verdict.allowed }
            .sortedByDescending { (_, verdict) -> verdict.score.total }
    }

    /**
     * Determine trust level for content
     */
    private fun determineTrustLevel(content: ContentItem): TrustLevel {
        return TrustedChannelRule.getTrustLevel(content.channelId, content.channelName)
    }

    /**
     * Get engine info
     */
    fun getEngineInfo(): EngineInfo = EngineInfo(
        name = ENGINE_NAME,
        version = VERSION,
        rulesCount = sortedRules.size,
        ruleNames = sortedRules.map { it.name }
    )
}

/**
 * Engine information
 */
data class EngineInfo(
    val name: String,
    val version: String,
    val rulesCount: Int,
    val ruleNames: List<String>
)

/**
 * Extension to convert Video to ContentItem
 */
fun Video.toContentItem(): ContentItem = ContentItem(
    id = id,
    title = title,
    description = description,
    channelId = channelId,
    channelName = channelName,
    duration = duration,
    viewCount = viewCount,
    category = category?.name
)

/**
 * Extension to convert AgeRating to KidzSafeRating
 */
fun AgeRating.toKidzSafeRating(): KidzSafeRating = when (this) {
    AgeRating.ALL -> KidzSafeRating.ALL
    AgeRating.FIVE_PLUS -> KidzSafeRating.UNDER_5
    AgeRating.EIGHT_PLUS -> KidzSafeRating.UNDER_8
    AgeRating.THIRTEEN_PLUS -> KidzSafeRating.UNDER_13
    AgeRating.SIXTEEN_PLUS -> KidzSafeRating.UNDER_16
}

/**
 * Extension to convert KidzSafeRating to AgeRating
 */
fun KidzSafeRating.toAgeRating(): AgeRating = when (this) {
    KidzSafeRating.ALL -> AgeRating.ALL
    KidzSafeRating.UNDER_5 -> AgeRating.FIVE_PLUS
    KidzSafeRating.UNDER_8 -> AgeRating.EIGHT_PLUS
    KidzSafeRating.UNDER_10 -> AgeRating.EIGHT_PLUS  // Map to closest: 8
    KidzSafeRating.UNDER_12 -> AgeRating.THIRTEEN_PLUS  // Map to closest: 13
    KidzSafeRating.UNDER_13 -> AgeRating.THIRTEEN_PLUS
    KidzSafeRating.UNDER_14 -> AgeRating.THIRTEEN_PLUS  // Map to closest: 13
    KidzSafeRating.UNDER_16 -> AgeRating.SIXTEEN_PLUS
}
