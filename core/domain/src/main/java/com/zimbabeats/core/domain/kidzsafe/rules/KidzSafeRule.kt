package com.zimbabeats.core.domain.kidzsafe.rules

import com.zimbabeats.core.domain.kidzsafe.model.ContentItem
import com.zimbabeats.core.domain.kidzsafe.model.KidzSafeRating
import com.zimbabeats.core.domain.kidzsafe.model.TrustLevel

/**
 * KidzSafe Engine - Rule Interface
 *
 * Base interface for all content filtering rules.
 * Rules are evaluated in priority order (higher priority first).
 */
interface KidzSafeRule {
    /**
     * Unique identifier for this rule
     */
    val id: String

    /**
     * Display name for the rule
     */
    val name: String

    /**
     * Priority (0-200). Higher priority rules are evaluated first.
     * - 200-150: Critical rules (blocklist, trusted channels)
     * - 149-100: Primary content rules
     * - 99-50: Secondary analysis rules
     * - 49-0: Scoring adjustment rules
     */
    val priority: Int

    /**
     * Whether this rule can immediately block or approve content
     */
    val isDecisive: Boolean

    /**
     * Evaluate the rule against content
     *
     * @param content The content item to evaluate
     * @param context The evaluation context
     * @return The result of the evaluation
     */
    suspend fun evaluate(content: ContentItem, context: RuleContext): RuleResult
}

/**
 * Context for rule evaluation
 */
data class RuleContext(
    val rating: KidzSafeRating,
    val trustLevel: TrustLevel,
    val strictMode: Boolean = false,
    val previousResults: List<RuleResult> = emptyList()
) {
    /**
     * Check if any previous rule has already blocked the content
     */
    val isAlreadyBlocked: Boolean
        get() = previousResults.any { it.action == RuleAction.BLOCK }

    /**
     * Check if any previous rule has already approved the content
     */
    val isAlreadyApproved: Boolean
        get() = previousResults.any { it.action == RuleAction.APPROVE }

    /**
     * Get current accumulated score adjustment
     */
    val accumulatedScoreAdjustment: Int
        get() = previousResults.sumOf { it.scoreAdjustment }
}

/**
 * Result of a rule evaluation
 */
data class RuleResult(
    val ruleId: String,
    val action: RuleAction,
    val scoreAdjustment: Int = 0,
    val reason: String? = null,
    val matchedPatterns: List<String> = emptyList()
) {
    companion object {
        /**
         * Skip - rule doesn't apply
         */
        fun skip(ruleId: String) = RuleResult(
            ruleId = ruleId,
            action = RuleAction.CONTINUE,
            scoreAdjustment = 0
        )

        /**
         * Block content immediately
         */
        fun block(ruleId: String, reason: String, patterns: List<String> = emptyList()) = RuleResult(
            ruleId = ruleId,
            action = RuleAction.BLOCK,
            scoreAdjustment = -1000,
            reason = reason,
            matchedPatterns = patterns
        )

        /**
         * Approve content immediately
         */
        fun approve(ruleId: String, reason: String) = RuleResult(
            ruleId = ruleId,
            action = RuleAction.APPROVE,
            scoreAdjustment = 0,
            reason = reason
        )

        /**
         * Adjust score and continue
         */
        fun adjustScore(ruleId: String, adjustment: Int, reason: String? = null) = RuleResult(
            ruleId = ruleId,
            action = RuleAction.CONTINUE,
            scoreAdjustment = adjustment,
            reason = reason
        )
    }
}

/**
 * Actions a rule can take
 */
enum class RuleAction {
    /**
     * Immediately block the content
     */
    BLOCK,

    /**
     * Immediately approve the content
     */
    APPROVE,

    /**
     * Continue to the next rule
     */
    CONTINUE
}

/**
 * Abstract base class for keyword-based rules
 */
abstract class KeywordBasedRule : KidzSafeRule {
    /**
     * Get the keywords to check for this rule
     */
    abstract fun getKeywords(context: RuleContext): Set<String>

    /**
     * Check if text contains any of the keywords
     */
    protected fun findMatchingKeywords(text: String, keywords: Set<String>): List<String> {
        val normalizedText = normalizeText(text)
        return keywords.filter { keyword ->
            normalizedText.contains(keyword.lowercase())
        }
    }

    /**
     * Normalize text for comparison
     */
    protected fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
