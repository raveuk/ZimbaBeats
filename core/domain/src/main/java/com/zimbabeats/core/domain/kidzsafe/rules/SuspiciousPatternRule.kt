package com.zimbabeats.core.domain.kidzsafe.rules

import com.zimbabeats.core.domain.kidzsafe.model.ContentItem
import com.zimbabeats.core.domain.kidzsafe.model.KidzSafeRating

/**
 * KidzSafe Rule - Suspicious Pattern Rule
 *
 * Detects Elsagate-style content and other suspicious patterns
 * that appear kid-friendly but may contain inappropriate material.
 */
class SuspiciousPatternRule : KeywordBasedRule() {

    override val id: String = "suspicious_pattern"
    override val name: String = "Suspicious Pattern Detection"
    override val priority: Int = 170
    override val isDecisive: Boolean = true

    override fun getKeywords(context: RuleContext): Set<String> = SUSPICIOUS_PATTERNS

    override suspend fun evaluate(content: ContentItem, context: RuleContext): RuleResult {
        // Only apply to strict ratings (Under 5 and Under 10)
        if (context.rating !in listOf(KidzSafeRating.UNDER_5, KidzSafeRating.UNDER_10)) {
            return RuleResult.skip(id)
        }

        val matchedPatterns = findMatchingKeywords(content.textContent, SUSPICIOUS_PATTERNS)

        if (matchedPatterns.isEmpty()) {
            return RuleResult.skip(id)
        }

        // Check if content has strong safe indicators that override suspicion
        val hasSafeIndicators = findMatchingKeywords(content.textContent, STRONG_SAFE_INDICATORS).isNotEmpty()
        val hasChannelIndicators = findMatchingKeywords(content.channelName.lowercase(), STRONG_SAFE_INDICATORS).isNotEmpty()

        if (hasSafeIndicators || hasChannelIndicators) {
            // Has both suspicious and safe indicators - reduce score but don't block
            return RuleResult.adjustScore(
                ruleId = id,
                adjustment = -50,
                reason = "Suspicious pattern detected but has safe indicators"
            )
        }

        // For Under 5, block suspicious patterns without safe indicators
        if (context.rating == KidzSafeRating.UNDER_5) {
            return RuleResult.block(
                ruleId = id,
                reason = "Blocked: Suspicious content pattern detected (Elsagate protection)",
                patterns = matchedPatterns
            )
        }

        // For Under 10, penalize heavily but don't auto-block
        return RuleResult.adjustScore(
            ruleId = id,
            adjustment = -150,
            reason = "Suspicious pattern penalty: ${matchedPatterns.firstOrNull()}"
        )
    }

    companion object {
        /**
         * Elsagate-style suspicious patterns
         */
        val SUSPICIOUS_PATTERNS = setOf(
            // Elsagate patterns
            "surprise egg", "surprise toys", "surprise",
            "wrong heads", "wrong head", "bad baby", "bad babies",
            "learn colors with", "learning colors with",
            "finger family", "daddy finger",
            "johny johny", "johnny johnny",
            "crying baby", "baby crying", "babies crying",
            "injection", "needle", "doctor baby", "baby doctor",
            "pregnant", "pregnancy", "baby born",

            // Inappropriate humor
            "poop", "pooping", "potty", "toilet humor",
            "fart", "farting", "burp", "burping",

            // Scary content
            "spider", "spiders", "snake", "snakes",

            // Problematic character mashups
            "joker elsa", "spiderman elsa", "hulk elsa", "frozen spiderman",

            // Low-quality patterns
            "real life", "in real life", "irl",
            "mukbang", "eating show", "asmr eating",
            "prank", "pranks", "pranking",
            "weird", "strange", "odd",
            "try not to laugh",
            "spaghetti face", "noodle face", "food face", "messy eating"
        )

        /**
         * Strong safe indicators that override suspicion
         */
        val STRONG_SAFE_INDICATORS = setOf(
            "cocomelon", "pinkfong", "super simple", "little baby bum",
            "sesame street", "pbs kids", "nick jr", "disney junior",
            "numberblocks", "alphablocks", "bluey", "peppa pig",
            "paw patrol", "blippi", "ms rachel", "hey bear sensory",
            "nursery rhymes", "abc song", "alphabet song",
            "phonics", "counting", "shapes and colors",
            "educational", "learning", "preschool"
        )
    }
}
