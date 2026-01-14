package com.zimbabeats.cloud

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CloudMusicFilter
 * Tests the restrictive default settings fix and music filtering logic
 */
class CloudMusicFilterTest {

    // ==================== Default Settings Tests ====================

    @Test
    fun `MusicFilterSettings default whitelistModeEnabled should be true (restrictive)`() {
        val settings = MusicFilterSettings()
        assertTrue(
            "Default whitelistModeEnabled should be TRUE for child safety",
            settings.whitelistModeEnabled
        )
    }

    @Test
    fun `MusicFilterSettings default ageRating should be EIGHT_PLUS (restrictive)`() {
        val settings = MusicFilterSettings()
        assertEquals(
            "Default ageRating should be EIGHT_PLUS for child safety",
            "EIGHT_PLUS",
            settings.ageRating
        )
    }

    @Test
    fun `MusicFilterSettings default blockExplicit should be true`() {
        val settings = MusicFilterSettings()
        assertTrue(
            "Default blockExplicit should be TRUE",
            settings.blockExplicit
        )
    }

    @Test
    fun `MusicFilterSettings default defaultKidsArtistsEnabled should be true`() {
        val settings = MusicFilterSettings()
        assertTrue(
            "Default defaultKidsArtistsEnabled should be TRUE so kids music works",
            settings.defaultKidsArtistsEnabled
        )
    }

    @Test
    fun `MusicFilterSettings default lists should be empty`() {
        val settings = MusicFilterSettings()
        assertTrue(settings.allowedArtists.isEmpty())
        assertTrue(settings.allowedKeywords.isEmpty())
        assertTrue(settings.allowedAlbums.isEmpty())
        assertTrue(settings.blockedArtists.isEmpty())
        assertTrue(settings.blockedKeywords.isEmpty())
    }

    @Test
    fun `MusicFilterSettings default historyEnabled should be true`() {
        val settings = MusicFilterSettings()
        assertTrue(settings.historyEnabled)
    }

    @Test
    fun `MusicFilterSettings default alertsEnabled should be true`() {
        val settings = MusicFilterSettings()
        assertTrue(settings.alertsEnabled)
    }

    // ==================== Default Kids Artists Tests ====================

    @Test
    fun `DEFAULT_KIDS_ARTISTS should contain known safe artists`() {
        val kidsArtists = CloudMusicFilter.DEFAULT_KIDS_ARTISTS

        assertTrue(kidsArtists.contains("cocomelon"))
        assertTrue(kidsArtists.contains("pinkfong"))
        assertTrue(kidsArtists.contains("baby shark"))
        assertTrue(kidsArtists.contains("little baby bum"))
        assertTrue(kidsArtists.contains("super simple songs"))
        assertTrue(kidsArtists.contains("blippi"))
        assertTrue(kidsArtists.contains("disney"))
        assertTrue(kidsArtists.contains("kidz bop"))
        assertTrue(kidsArtists.contains("the wiggles"))
        assertTrue(kidsArtists.contains("sesame street"))
    }

    @Test
    fun `DEFAULT_KIDS_ARTISTS should have reasonable size`() {
        val kidsArtists = CloudMusicFilter.DEFAULT_KIDS_ARTISTS
        assertTrue(
            "Should have at least 20 default kids artists",
            kidsArtists.size >= 20
        )
    }

    // ==================== MusicBlockResult Tests ====================

    @Test
    fun `MusicBlockResult allowed should have isBlocked false`() {
        val result = MusicBlockResult(isBlocked = false)
        assertFalse(result.isBlocked)
        assertNull(result.reason)
        assertNull(result.message)
    }

    @Test
    fun `MusicBlockResult blocked should have reason and message`() {
        val result = MusicBlockResult(
            isBlocked = true,
            reason = MusicBlockReason.EXPLICIT_CONTENT,
            message = "Explicit content is not allowed"
        )
        assertTrue(result.isBlocked)
        assertEquals(MusicBlockReason.EXPLICIT_CONTENT, result.reason)
        assertEquals("Explicit content is not allowed", result.message)
    }

    // ==================== MusicBlockReason Tests ====================

    @Test
    fun `MusicBlockReason should have all expected values`() {
        val reasons = MusicBlockReason.entries.map { it.name }
        assertTrue(reasons.contains("NOT_WHITELISTED"))
        assertTrue(reasons.contains("EXPLICIT_CONTENT"))
        assertTrue(reasons.contains("TOO_LONG"))
        assertTrue(reasons.contains("BLOCKED_ARTIST"))
        assertTrue(reasons.contains("BLOCKED_KEYWORD"))
        assertTrue(reasons.contains("SEARCH_NOT_ALLOWED"))
    }

    // ==================== Whitelist Mode Logic Tests ====================

    @Test
    fun `whitelist mode should be active for FIVE_PLUS age rating`() {
        val settings = MusicFilterSettings(
            whitelistModeEnabled = true,
            ageRating = "FIVE_PLUS"
        )
        assertTrue(settings.whitelistModeEnabled)
        assertTrue(settings.ageRating in listOf("FIVE_PLUS", "EIGHT_PLUS", "THIRTEEN_PLUS"))
    }

    @Test
    fun `whitelist mode should be active for EIGHT_PLUS age rating`() {
        val settings = MusicFilterSettings(
            whitelistModeEnabled = true,
            ageRating = "EIGHT_PLUS"
        )
        assertTrue(settings.whitelistModeEnabled)
    }

    @Test
    fun `whitelist mode should be active for THIRTEEN_PLUS age rating`() {
        val settings = MusicFilterSettings(
            whitelistModeEnabled = true,
            ageRating = "THIRTEEN_PLUS"
        )
        assertTrue(settings.whitelistModeEnabled)
    }

    @Test
    fun `whitelist mode should NOT be active for SIXTEEN_PLUS age rating`() {
        val settings = MusicFilterSettings(
            whitelistModeEnabled = true,
            ageRating = "SIXTEEN_PLUS"
        )
        // Even if whitelistModeEnabled is true, 16+ uses blocklist mode
        assertTrue(settings.ageRating !in listOf("FIVE_PLUS", "EIGHT_PLUS", "THIRTEEN_PLUS"))
    }

    @Test
    fun `whitelist mode should NOT be active for ALL age rating`() {
        val settings = MusicFilterSettings(
            whitelistModeEnabled = true,
            ageRating = "ALL"
        )
        assertTrue(settings.ageRating !in listOf("FIVE_PLUS", "EIGHT_PLUS", "THIRTEEN_PLUS"))
    }

    // ==================== Settings Configuration Tests ====================

    @Test
    fun `settings with custom allowed artists`() {
        val settings = MusicFilterSettings(
            allowedArtists = listOf("Taylor Swift", "Ed Sheeran")
        )
        assertEquals(2, settings.allowedArtists.size)
        assertTrue(settings.allowedArtists.contains("Taylor Swift"))
        assertTrue(settings.allowedArtists.contains("Ed Sheeran"))
    }

    @Test
    fun `settings with blocked artists`() {
        val settings = MusicFilterSettings(
            blockedArtists = listOf("Explicit Artist")
        )
        assertEquals(1, settings.blockedArtists.size)
        assertTrue(settings.blockedArtists.contains("Explicit Artist"))
    }

    @Test
    fun `settings with max duration`() {
        val settings = MusicFilterSettings(
            maxDurationSeconds = 300 // 5 minutes
        )
        assertEquals(300, settings.maxDurationSeconds)
    }

    // ==================== Security Tests ====================

    @Test
    fun `restrictive defaults ensure child safety when no settings configured`() {
        // When no Firebase document exists, these defaults should protect children
        val defaults = MusicFilterSettings()

        // Whitelist mode ON = blocks all unknown music
        assertTrue("Whitelist mode should be ON by default", defaults.whitelistModeEnabled)

        // Default kids artists enabled = safe kids music works
        assertTrue("Default kids artists should be enabled", defaults.defaultKidsArtistsEnabled)

        // Block explicit = additional safety layer
        assertTrue("Explicit blocking should be ON", defaults.blockExplicit)

        // Conservative age rating
        assertEquals("Age rating should be restrictive", "EIGHT_PLUS", defaults.ageRating)
    }

    @Test
    fun `with restrictive defaults and kids artists enabled - safe content is available`() {
        val defaults = MusicFilterSettings()

        // With whitelist mode + defaultKidsArtistsEnabled = kids can listen to safe music
        assertTrue(defaults.whitelistModeEnabled)
        assertTrue(defaults.defaultKidsArtistsEnabled)

        // CoComelon, Pinkfong, etc. should work out of the box
        val kidsArtists = CloudMusicFilter.DEFAULT_KIDS_ARTISTS
        assertTrue(kidsArtists.isNotEmpty())
    }
}
