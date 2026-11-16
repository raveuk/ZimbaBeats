package com.zimbabeats.shared.filter

import com.zimbabeats.shared.model.AgeRatingFilter
import com.zimbabeats.shared.model.SharedVideo

/**
 * Multiplatform KidzSafe Content Filter Engine shared across Android and iOS.
 */
object KidzSafeFilter {

    private val globalBlockedKeywords = listOf(
        "18+", "nsfw", "explicit", "uncensored", "gore", "horror", "jumpscare",
        "elsagate", "skibidi", "huggy wuggy", "poppy playtime", "siren head",
        "mommy long legs", "grimace shake", "happy tree friends", "garten of banban",
        "kill", "suicide", "blood", "drugs", "gambling", "casino", "violence"
    )

    private val strictUnder5BlockedKeywords = listOf(
        "prank", "scary", "zombie", "monster", "fight", "weapon", "gun", "sword",
        "creepypasta", "fortnite", "GTA", "call of duty", "roblox horror"
    )

    fun isSafe(video: SharedVideo, ageRating: AgeRatingFilter = AgeRatingFilter.UNDER_8): Boolean {
        val text = "${video.title} ${video.channelName}".lowercase()

        if (globalBlockedKeywords.any { keyword -> text.contains(keyword) }) {
            return false
        }

        if (ageRating == AgeRatingFilter.UNDER_5) {
            if (strictUnder5BlockedKeywords.any { keyword -> text.contains(keyword) }) {
                return false
            }
        }

        return true
    }

    fun filterVideos(
        videos: List<SharedVideo>,
        ageRating: AgeRatingFilter = AgeRatingFilter.UNDER_8
    ): List<SharedVideo> {
        return videos.filter { isSafe(it, ageRating) }
    }
}
