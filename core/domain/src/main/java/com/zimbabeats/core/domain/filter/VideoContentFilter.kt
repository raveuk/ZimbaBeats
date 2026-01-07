package com.zimbabeats.core.domain.filter

import com.zimbabeats.core.domain.model.AgeRating
import com.zimbabeats.core.domain.model.ParentalSettings
import com.zimbabeats.core.domain.model.Video

class VideoContentFilter {

    /**
     * Filter video using AgeGroup (standalone filtering)
     */
    fun filterVideo(video: Video, ageGroup: AgeGroup): FilterResult {
        val config = FilterConfig.ageConfigs[ageGroup] ?: return FilterResult(false, "invalid_age_group", 0)
        val blocklist = FilterConfig.blocklistKeywords[ageGroup] ?: emptySet()

        // Whitelist check - auto-approve trusted channels
        if (isWhitelistedChannel(video.channelId, ageGroup)) {
            return FilterResult(allowed = true, score = 100)
        }

        // Check if channel name is a known kid-safe channel
        if (isKnownKidSafeChannel(video.channelName)) {
            return FilterResult(allowed = true, score = 95)
        }

        // Duration check (convert video duration from ms to seconds if needed)
        val durationSeconds = if (video.duration > 100000) video.duration / 1000 else video.duration
        if (durationSeconds > config.maxDuration) {
            return FilterResult(allowed = false, reason = "duration_exceeded", score = 0)
        }

        // Keyword blocklist check
        val textContent = buildString {
            append(video.title.lowercase())
            append(" ")
            append(video.channelName.lowercase())
            append(" ")
            video.description?.let { append(it.lowercase()) }
        }

        val blockedKeyword = findBlockedKeyword(textContent, blocklist)
        if (blockedKeyword != null) {
            return FilterResult(allowed = false, reason = "blocked_keyword:$blockedKeyword", score = 0)
        }

        // Category check (strict mode only)
        if (config.strictMode && video.category != null && video.category !in config.allowedCategories) {
            return FilterResult(allowed = false, reason = "category_not_allowed:${video.category}", score = 0)
        }

        // Check for suspicious Elsagate-style content
        val hasSuspiciousPattern = containsSuspiciousKeyword(textContent)

        // For Under 5: If suspicious pattern found, require STRONG indicators (trusted brands only)
        if (ageGroup == AgeGroup.UNDER_5 && hasSuspiciousPattern) {
            val hasStrongIndicator = containsStrongSafeIndicator(textContent) ||
                containsStrongSafeIndicator(video.channelName.lowercase())

            if (!hasStrongIndicator) {
                return FilterResult(
                    allowed = false,
                    reason = "suspicious_content_pattern",
                    score = 20
                )
            }
        }

        // WHITELIST-FIRST for younger kids: Must have kid-friendly indicators
        // For Under 5 and Under 10, require positive indicators (not just absence of bad content)
        if (config.strictMode) {
            val hasSafeIndicator = containsSafeKeyword(textContent)
            val hasKidFriendlyChannel = containsSafeKeyword(video.channelName.lowercase())

            // For Under 5: Be extra strict - suspicious content blocked unless from trusted source
            if (ageGroup == AgeGroup.UNDER_5) {
                if (hasSuspiciousPattern) {
                    return FilterResult(
                        allowed = false,
                        reason = "suspicious_for_under_5",
                        score = 25
                    )
                }
            }

            if (!hasSafeIndicator && !hasKidFriendlyChannel) {
                return FilterResult(
                    allowed = false,
                    reason = "no_kid_friendly_indicator",
                    score = 30
                )
            }
        }

        // Calculate score
        val score = calculateVideoScore(video, ageGroup, config)
        val threshold = if (config.strictMode) 60 else 45

        if (score < threshold) {
            return FilterResult(allowed = false, reason = "low_score", score = score)
        }

        return FilterResult(allowed = true, score = score)
    }

    /**
     * Filter video using ParentalSettings (integrated with existing system)
     */
    fun filterVideoWithSettings(video: Video, settings: ParentalSettings): FilterResult {
        // If parental controls disabled, allow all
        if (!settings.isEnabled) {
            return FilterResult(allowed = true, score = 100)
        }

        // First check with existing ParentalSettings filter
        if (!settings.isVideoAllowed(video)) {
            return FilterResult(allowed = false, reason = "parental_settings_blocked", score = 0)
        }

        // Then apply score-based filtering
        val ageGroup = ageRatingToAgeGroup(settings.selectedAgeLevel)
        return filterVideo(video, ageGroup)
    }

    /**
     * Filter list of videos using AgeGroup
     */
    fun filterVideos(videos: List<Video>, ageGroup: AgeGroup): List<Video> {
        return videos
            .map { video -> video to filterVideo(video, ageGroup) }
            .filter { (_, result) -> result.allowed }
            .sortedByDescending { (_, result) -> result.score }
            .map { (video, _) -> video }
    }

    /**
     * Filter list of videos using ParentalSettings
     */
    fun filterVideosWithSettings(videos: List<Video>, settings: ParentalSettings): List<Video> {
        if (!settings.isEnabled) return videos

        return videos
            .map { video -> video to filterVideoWithSettings(video, settings) }
            .filter { (_, result) -> result.allowed }
            .sortedByDescending { (_, result) -> result.score }
            .map { (video, _) -> video }
    }

    /**
     * Get safety score for a video (0-100)
     */
    fun getVideoSafetyScore(video: Video, ageGroup: AgeGroup): Int {
        val config = FilterConfig.ageConfigs[ageGroup] ?: return 0
        return calculateVideoScore(video, ageGroup, config)
    }

    private fun ageRatingToAgeGroup(ageRating: AgeRating): AgeGroup {
        return when (ageRating) {
            AgeRating.ALL -> AgeGroup.UNDER_16
            AgeRating.FIVE_PLUS -> AgeGroup.UNDER_5
            AgeRating.EIGHT_PLUS -> AgeGroup.UNDER_8
            AgeRating.TEN_PLUS -> AgeGroup.UNDER_10
            AgeRating.TWELVE_PLUS -> AgeGroup.UNDER_12
            AgeRating.THIRTEEN_PLUS -> AgeGroup.UNDER_13
            AgeRating.FOURTEEN_PLUS -> AgeGroup.UNDER_14
            AgeRating.SIXTEEN_PLUS -> AgeGroup.UNDER_16
        }
    }

    private fun isWhitelistedChannel(channelId: String, ageGroup: AgeGroup): Boolean {
        return AgeGroup.entries
            .filter { it.maxAge <= ageGroup.maxAge }
            .any { group ->
                FilterConfig.whitelistedChannels[group]?.contains(channelId) == true
            }
    }

    private fun isKnownKidSafeChannel(channelName: String): Boolean {
        val nameLower = channelName.lowercase()
        return FilterConfig.trustedChannelNames.any { trusted ->
            nameLower.contains(trusted.lowercase())
        }
    }

    private fun findBlockedKeyword(text: String, blocklist: Set<String>): String? {
        val normalized = normalizeText(text)
        return blocklist.firstOrNull { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }

    private fun containsSafeKeyword(text: String): Boolean {
        val normalized = normalizeText(text)
        return FilterConfig.safeKeywords.any { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }

    private fun containsSuspiciousKeyword(text: String): Boolean {
        val normalized = normalizeText(text)
        return FilterConfig.suspiciousKeywords.any { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }

    private fun containsStrongSafeIndicator(text: String): Boolean {
        val normalized = normalizeText(text)
        return FilterConfig.strongSafeIndicators.any { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun getChannelScore(channelId: String): Int {
        return FilterConfig.channelReputation[channelId] ?: 50
    }

    private fun calculateVideoScore(video: Video, ageGroup: AgeGroup, config: AgeConfig): Int {
        var score = 50

        // Channel reputation boost
        score += ((getChannelScore(video.channelId) - 50) * 0.3).toInt()

        // Safe keyword boost
        val textContent = "${video.title} ${video.description ?: ""}"
        if (containsSafeKeyword(textContent)) {
            score += 15
        }

        // Category match boost
        if (video.category != null && video.category in config.allowedCategories) {
            score += 10
        }

        // Duration bonus (shorter is better for younger kids)
        val durationSeconds = if (video.duration > 100000) video.duration / 1000 else video.duration
        if (durationSeconds <= config.maxDuration * 0.5) {
            score += 5
        }

        // Title length bonus
        if (video.title.length <= config.maxTitleLength * 0.7) {
            score += 5
        }

        // Channel name keyword check
        val channelLower = video.channelName.lowercase()
        if (FilterConfig.safeKeywords.any { channelLower.contains(it) }) {
            score += 10
        }

        return score.coerceIn(0, 100)
    }
}
