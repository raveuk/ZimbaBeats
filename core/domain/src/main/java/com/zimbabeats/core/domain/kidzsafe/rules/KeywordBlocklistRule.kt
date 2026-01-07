package com.zimbabeats.core.domain.kidzsafe.rules

import com.zimbabeats.core.domain.kidzsafe.model.ContentItem
import com.zimbabeats.core.domain.kidzsafe.model.KidzSafeRating

/**
 * KidzSafe Rule - Keyword Blocklist Rule
 *
 * Blocks content containing age-inappropriate keywords.
 * Uses different keyword sets based on the age rating.
 */
class KeywordBlocklistRule : KeywordBasedRule() {

    override val id: String = "keyword_blocklist"
    override val name: String = "Keyword Blocklist Rule"
    override val priority: Int = 180  // Very high priority
    override val isDecisive: Boolean = true

    override fun getKeywords(context: RuleContext): Set<String> {
        return when (context.rating) {
            KidzSafeRating.UNDER_5 -> BLOCKED_UNDER_5
            KidzSafeRating.UNDER_8 -> BLOCKED_UNDER_10  // Use stricter UNDER_10 for 8
            KidzSafeRating.UNDER_10 -> BLOCKED_UNDER_10
            KidzSafeRating.UNDER_12 -> BLOCKED_UNDER_12
            KidzSafeRating.UNDER_13 -> BLOCKED_UNDER_14  // Use UNDER_14 for 13
            KidzSafeRating.UNDER_14 -> BLOCKED_UNDER_14
            KidzSafeRating.UNDER_16 -> BLOCKED_UNDER_16
            KidzSafeRating.ALL -> emptySet()
        }
    }

    override suspend fun evaluate(content: ContentItem, context: RuleContext): RuleResult {
        // Skip for ALL ages - no blocking
        if (context.rating == KidzSafeRating.ALL) {
            return RuleResult.skip(id)
        }

        val keywords = getKeywords(context)
        val matchedKeywords = findMatchingKeywords(content.textContent, keywords)

        if (matchedKeywords.isNotEmpty()) {
            return RuleResult.block(
                ruleId = id,
                reason = "Blocked: Contains inappropriate content for ${context.rating.displayName}",
                patterns = matchedKeywords
            )
        }

        return RuleResult.skip(id)
    }

    companion object {
        /**
         * Blocked for Under 5 - Maximum protection
         */
        val BLOCKED_UNDER_5 = setOf(
            // Violence
            "scary", "horror", "blood", "kill", "death", "gun", "weapon", "fight", "war",
            "violence", "murder", "suicide", "abuse", "creepy", "nightmare",
            "demon", "evil", "hell", "satan", "curse", "monster", "zombie",
            "slender", "fnaf", "jumpscare", "haunted", "ghost", "scream",
            "disturbing", "graphic", "bloody", "gory", "violent", "dark", "twisted", "insane",

            // Adult content
            "drug", "alcohol", "beer", "wine", "smoking", "cigarette",
            "sex", "nude", "adult", "mature", "explicit", "uncensored",
            "18+", "nsfw", "gone sexual", "r-rated", "pg-13",

            // Negative behavior
            "prank gone wrong", "hate", "damn", "crap", "stupid", "idiot",

            // Clickbait patterns
            "clickbait"
        )

        /**
         * Blocked for Under 10
         */
        val BLOCKED_UNDER_10 = setOf(
            "horror", "blood", "kill", "death", "gun", "weapon",
            "drug", "alcohol", "smoking", "sex", "nude", "adult", "mature",
            "violence", "murder", "suicide", "abuse",
            "demon", "satan", "r-rated", "explicit", "uncensored", "18+", "nsfw", "gone sexual",
            "slender", "jumpscare", "disturbing", "graphic", "bloody", "gory",
            "how to watch adult", "bypass age", "age restriction", "restricted content"
        )

        /**
         * Blocked for Under 12
         */
        val BLOCKED_UNDER_12 = setOf(
            "horror", "blood", "gore", "drug", "alcohol",
            "sex", "nude", "adult", "mature", "murder", "suicide",
            "r-rated", "explicit", "18+", "nsfw", "gone sexual",
            "disturbing", "graphic content",
            "how to watch adult", "bypass age", "age restriction", "restricted content"
        )

        /**
         * Blocked for Under 14
         */
        val BLOCKED_UNDER_14 = setOf(
            "sex", "nude", "porn", "adult", "adult only", "explicit content", "explicit",
            "18+", "nsfw", "xxx", "mature", "r-rated", "x-rated",
            "gore", "torture", "drug use", "suicide method", "self harm",
            "how to watch adult", "bypass age", "age restriction", "restricted content"
        )

        /**
         * Blocked for Under 16
         */
        val BLOCKED_UNDER_16 = setOf(
            "sex", "nude", "porn", "adult only", "explicit content",
            "18+", "nsfw", "xxx", "x-rated",
            "torture", "drug use", "suicide method", "self harm"
        )
    }
}
