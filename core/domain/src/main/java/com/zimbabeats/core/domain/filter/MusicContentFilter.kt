package com.zimbabeats.core.domain.filter

import com.zimbabeats.core.domain.model.AgeRating
import com.zimbabeats.core.domain.model.ParentalSettings
import com.zimbabeats.core.domain.model.music.Track

/**
 * Content filter for music tracks
 * Filters music based on age-appropriateness using keywords, artist reputation, and explicit flags
 */
class MusicContentFilter {

    /**
     * Filter track using AgeGroup (standalone filtering)
     */
    fun filterTrack(track: Track, ageGroup: AgeGroup): FilterResult {
        val config = MusicFilterConfig.ageConfigs[ageGroup]
            ?: return FilterResult(false, "invalid_age_group", 0)
        val blocklist = MusicFilterConfig.blocklistKeywords[ageGroup] ?: emptySet()

        // Explicit content check - blocked for all kid age groups
        if (track.isExplicit && config.blockExplicit) {
            return FilterResult(
                allowed = false,
                reason = "explicit_content",
                score = 0
            )
        }

        // Whitelisted artist check - auto-approve trusted kid artists
        if (isWhitelistedArtist(track.artistId, track.artistName, ageGroup)) {
            return FilterResult(allowed = true, score = 100)
        }

        // Known kid-safe artist check
        if (isKnownKidSafeArtist(track.artistName)) {
            return FilterResult(allowed = true, score = 95)
        }

        // Duration check (track duration in milliseconds)
        val durationSeconds = track.duration / 1000
        if (durationSeconds > config.maxDuration) {
            return FilterResult(allowed = false, reason = "duration_exceeded", score = 0)
        }

        // Build text content for keyword analysis
        val textContent = buildString {
            append(track.title.lowercase())
            append(" ")
            append(track.artistName.lowercase())
            append(" ")
            track.albumName?.let { append(it.lowercase()) }
        }

        // Keyword blocklist check
        val blockedKeyword = findBlockedKeyword(textContent, blocklist)
        if (blockedKeyword != null) {
            return FilterResult(
                allowed = false,
                reason = "blocked_keyword:$blockedKeyword",
                score = 0
            )
        }

        // Check for adult/inappropriate music themes
        val hasInappropriateTheme = containsInappropriateTheme(textContent)
        if (hasInappropriateTheme && config.strictMode) {
            return FilterResult(
                allowed = false,
                reason = "inappropriate_theme",
                score = 15
            )
        }

        // For younger kids (Under 5, Under 10), require positive kid-friendly indicators
        if (config.strictMode) {
            val hasSafeIndicator = containsSafeMusicKeyword(textContent)
            val hasKidFriendlyArtist = containsSafeMusicKeyword(track.artistName.lowercase())

            if (!hasSafeIndicator && !hasKidFriendlyArtist) {
                // For Under 5: Be extra strict
                if (ageGroup == AgeGroup.UNDER_5) {
                    return FilterResult(
                        allowed = false,
                        reason = "no_kid_friendly_indicator",
                        score = 25
                    )
                }
                // For Under 10: Penalize but allow if score is decent
            }
        }

        // Calculate score
        val score = calculateTrackScore(track, ageGroup, config)
        val threshold = if (config.strictMode) 55 else 40

        if (score < threshold) {
            return FilterResult(allowed = false, reason = "low_score", score = score)
        }

        return FilterResult(allowed = true, score = score)
    }

    /**
     * Filter track using ParentalSettings (integrated with existing system)
     */
    fun filterTrackWithSettings(track: Track, settings: ParentalSettings): FilterResult {
        // If parental controls disabled, allow all
        if (!settings.isEnabled) {
            return FilterResult(allowed = true, score = 100)
        }

        // Apply score-based filtering based on age level
        val ageGroup = ageRatingToAgeGroup(settings.selectedAgeLevel)
        return filterTrack(track, ageGroup)
    }

    /**
     * Filter list of tracks using AgeGroup
     */
    fun filterTracks(tracks: List<Track>, ageGroup: AgeGroup): List<Track> {
        return tracks
            .map { track -> track to filterTrack(track, ageGroup) }
            .filter { (_, result) -> result.allowed }
            .sortedByDescending { (_, result) -> result.score }
            .map { (track, _) -> track }
    }

    /**
     * Filter list of tracks using ParentalSettings
     */
    fun filterTracksWithSettings(tracks: List<Track>, settings: ParentalSettings): List<Track> {
        if (!settings.isEnabled) return tracks

        return tracks
            .map { track -> track to filterTrackWithSettings(track, settings) }
            .filter { (_, result) -> result.allowed }
            .sortedByDescending { (_, result) -> result.score }
            .map { (track, _) -> track }
    }

    /**
     * Get safety score for a track (0-100)
     */
    fun getTrackSafetyScore(track: Track, ageGroup: AgeGroup): Int {
        val config = MusicFilterConfig.ageConfigs[ageGroup] ?: return 0
        return calculateTrackScore(track, ageGroup, config)
    }

    /**
     * Quick check if track is appropriate (without full scoring)
     */
    fun isTrackAppropriate(track: Track, ageGroup: AgeGroup): Boolean {
        return filterTrack(track, ageGroup).allowed
    }

    private fun ageRatingToAgeGroup(ageRating: AgeRating): AgeGroup {
        return when (ageRating) {
            AgeRating.ALL -> AgeGroup.UNDER_16
            AgeRating.FIVE_PLUS -> AgeGroup.UNDER_5
            AgeRating.EIGHT_PLUS -> AgeGroup.UNDER_8
            AgeRating.THIRTEEN_PLUS -> AgeGroup.UNDER_13
            AgeRating.SIXTEEN_PLUS -> AgeGroup.UNDER_16
        }
    }

    private fun isWhitelistedArtist(artistId: String?, artistName: String, ageGroup: AgeGroup): Boolean {
        // Check by artist ID
        if (artistId != null) {
            val isWhitelisted = AgeGroup.entries
                .filter { it.maxAge <= ageGroup.maxAge }
                .any { group ->
                    MusicFilterConfig.whitelistedArtists[group]?.contains(artistId) == true
                }
            if (isWhitelisted) return true
        }

        // Check by artist name
        val nameLower = artistName.lowercase()
        return MusicFilterConfig.trustedArtistNames.any { trusted ->
            nameLower.contains(trusted.lowercase())
        }
    }

    private fun isKnownKidSafeArtist(artistName: String): Boolean {
        val nameLower = artistName.lowercase()
        return MusicFilterConfig.trustedArtistNames.any { trusted ->
            nameLower.contains(trusted.lowercase())
        }
    }

    private fun findBlockedKeyword(text: String, blocklist: Set<String>): String? {
        val normalized = normalizeText(text)
        return blocklist.firstOrNull { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }

    private fun containsSafeMusicKeyword(text: String): Boolean {
        val normalized = normalizeText(text)
        return MusicFilterConfig.safeMusicKeywords.any { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }

    private fun containsInappropriateTheme(text: String): Boolean {
        val normalized = normalizeText(text)
        return MusicFilterConfig.inappropriateMusicThemes.any { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun getArtistScore(artistId: String?, artistName: String): Int {
        // Check by ID first
        if (artistId != null) {
            MusicFilterConfig.artistReputation[artistId]?.let { return it }
        }
        // Check by name
        val nameLower = artistName.lowercase()
        MusicFilterConfig.trustedArtistNames.forEach { trusted ->
            if (nameLower.contains(trusted.lowercase())) {
                return 90
            }
        }
        return 50 // Default neutral score
    }

    private fun calculateTrackScore(track: Track, ageGroup: AgeGroup, config: MusicAgeConfig): Int {
        var score = 50

        // Explicit content heavy penalty
        if (track.isExplicit) {
            score -= 50
        }

        // Artist reputation boost
        score += ((getArtistScore(track.artistId, track.artistName) - 50) * 0.3).toInt()

        // Safe keyword boost
        val textContent = "${track.title} ${track.albumName ?: ""}"
        if (containsSafeMusicKeyword(textContent)) {
            score += 20
        }

        // Artist name keyword check
        if (containsSafeMusicKeyword(track.artistName.lowercase())) {
            score += 15
        }

        // Duration bonus (shorter is better for younger kids)
        val durationSeconds = track.duration / 1000
        if (durationSeconds <= config.maxDuration * 0.5) {
            score += 5
        }

        // Title analysis
        val titleLower = track.title.lowercase()

        // Bonus for kid-friendly title patterns
        if (titleLower.contains("lullaby") ||
            titleLower.contains("nursery") ||
            titleLower.contains("kids") ||
            titleLower.contains("children")) {
            score += 10
        }

        // Penalty for potentially inappropriate patterns
        if (containsInappropriateTheme(titleLower)) {
            score -= 20
        }

        return score.coerceIn(0, 100)
    }
}
