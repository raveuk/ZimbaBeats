package com.zimbabeats.core.domain.kidzsafe.rules

import com.zimbabeats.core.domain.kidzsafe.model.ContentItem
import com.zimbabeats.core.domain.kidzsafe.model.TrustLevel

/**
 * KidzSafe Rule - Trusted Channel Rule
 *
 * Auto-approves content from verified partner and trusted channels.
 * This is a high-priority decisive rule.
 */
class TrustedChannelRule : KidzSafeRule {

    override val id: String = "trusted_channel"
    override val name: String = "Trusted Channel Rule"
    override val priority: Int = 200  // Highest priority
    override val isDecisive: Boolean = true

    override suspend fun evaluate(content: ContentItem, context: RuleContext): RuleResult {
        // Skip if content is from blocked channel
        if (context.trustLevel.isBlocked) {
            return RuleResult.skip(id)
        }

        // Auto-approve if from verified partner or trusted source
        if (context.trustLevel.autoApprove) {
            return RuleResult.approve(
                ruleId = id,
                reason = "Approved: Content from ${context.trustLevel.displayName.lowercase()} channel '${content.channelName}'"
            )
        }

        return RuleResult.skip(id)
    }

    companion object {
        /**
         * Verified partner channel IDs (CoComelon, Pinkfong, etc.)
         */
        val VERIFIED_PARTNER_IDS = setOf(
            "UCBnZ16ahKA2DZ_T5W0FPUXg", // CoComelon
            "UC4NALVCmcmL5ntpKx19zoJQ", // Pinkfong
            "UCkQO3QsgTpNTsOw6ujimT5Q", // Blippi
            "UC-Gm4EN7nNNR3k67J8ywF4A", // Super Simple Songs
            "UCeirmJxuV9HM9VdG0ykDJhg", // Little Baby Bum
            "UCPPJnlbQSvdXhFOZXWqangg"  // BabyBus
        )

        /**
         * Trusted channel IDs (PBS Kids, Disney Junior, etc.)
         */
        val TRUSTED_IDS = setOf(
            "UC295-Dw_tDNtZXFeAPAQKEw", // National Geographic Kids
            "UCX6OQ3DkcsbYNE6H8uQQuVA", // Crash Course
            "UCvGQ8CnL3u7bE5BO8E0ZXzA", // SciShow Kids
            "UCsooa4yRKGN_zEE8iknghZA", // TED-Ed
            "UCsXVk37bltHxD1rDPwtNM8Q"  // Kurzgesagt
        )

        /**
         * Trusted channel name patterns (case-insensitive)
         */
        val TRUSTED_CHANNEL_NAMES = setOf(
            "cocomelon", "coco melon",
            "pinkfong", "baby shark",
            "little baby bum",
            "super simple songs", "super simple",
            "blippi",
            "ms rachel", "songs for littles",
            "dave and ava",
            "bounce patrol",
            "chu chu tv", "chutv",
            "sesame street",
            "pbs kids",
            "national geographic kids", "nat geo kids",
            "ted-ed", "teded",
            "crash course kids",
            "scishow kids",
            "numberblocks", "alphablocks",
            "peppa pig official",
            "paw patrol official",
            "bluey official",
            "disney junior",
            "nick jr",
            "cartoon network",
            "dreamworks tv",
            "hey bear sensory",
            "babybus", "baby bus",
            "little angel",
            "moonbug kids",
            "super jojo",
            "mother goose club",
            "little treehouse"
        )

        /**
         * Check if a channel is a verified partner
         */
        fun isVerifiedPartner(channelId: String): Boolean =
            channelId in VERIFIED_PARTNER_IDS

        /**
         * Check if a channel is trusted
         */
        fun isTrusted(channelId: String): Boolean =
            channelId in TRUSTED_IDS

        /**
         * Check if channel name matches a trusted pattern
         */
        fun matchesTrustedName(channelName: String): Boolean {
            val nameLower = channelName.lowercase()
            return TRUSTED_CHANNEL_NAMES.any { trusted ->
                nameLower.contains(trusted)
            }
        }

        /**
         * Get trust level for a channel
         */
        fun getTrustLevel(channelId: String, channelName: String): TrustLevel {
            return when {
                isVerifiedPartner(channelId) -> TrustLevel.VERIFIED_PARTNER
                isTrusted(channelId) -> TrustLevel.TRUSTED
                matchesTrustedName(channelName) -> TrustLevel.RECOGNIZED
                else -> TrustLevel.NEUTRAL
            }
        }
    }
}
