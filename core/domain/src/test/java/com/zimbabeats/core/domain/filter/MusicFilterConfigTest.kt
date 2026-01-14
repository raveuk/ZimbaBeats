package com.zimbabeats.core.domain.filter

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MusicFilterConfig
 * Tests the simplified age group configurations for music filtering
 */
class MusicFilterConfigTest {

    // ==================== Age Configs Tests ====================

    @Test
    fun `music ageConfigs should have exactly 4 entries`() {
        assertEquals(4, MusicFilterConfig.ageConfigs.size)
    }

    @Test
    fun `music ageConfigs should contain only simplified age groups`() {
        val keys = MusicFilterConfig.ageConfigs.keys
        assertTrue(keys.contains(AgeGroup.UNDER_5))
        assertTrue(keys.contains(AgeGroup.UNDER_8))
        assertTrue(keys.contains(AgeGroup.UNDER_13))
        assertTrue(keys.contains(AgeGroup.UNDER_16))
    }

    @Test
    fun `music ageConfigs should NOT contain removed age groups`() {
        val keys = MusicFilterConfig.ageConfigs.keys.map { it.name }
        assertFalse("UNDER_10 should not exist", keys.contains("UNDER_10"))
        assertFalse("UNDER_12 should not exist", keys.contains("UNDER_12"))
        assertFalse("UNDER_14 should not exist", keys.contains("UNDER_14"))
    }

    @Test
    fun `all music age configs should block explicit content`() {
        for ((ageGroup, config) in MusicFilterConfig.ageConfigs) {
            assertTrue("$ageGroup should block explicit content", config.blockExplicit)
        }
    }

    @Test
    fun `UNDER_5 music config should have strict mode`() {
        val config = MusicFilterConfig.ageConfigs[AgeGroup.UNDER_5]
        assertNotNull(config)
        assertTrue(config!!.strictMode)
    }

    @Test
    fun `UNDER_8 music config should have strict mode`() {
        val config = MusicFilterConfig.ageConfigs[AgeGroup.UNDER_8]
        assertNotNull(config)
        assertTrue(config!!.strictMode)
    }

    @Test
    fun `UNDER_13 music config should NOT have strict mode`() {
        val config = MusicFilterConfig.ageConfigs[AgeGroup.UNDER_13]
        assertNotNull(config)
        assertFalse(config!!.strictMode)
    }

    @Test
    fun `UNDER_16 music config should NOT have strict mode`() {
        val config = MusicFilterConfig.ageConfigs[AgeGroup.UNDER_16]
        assertNotNull(config)
        assertFalse(config!!.strictMode)
    }

    @Test
    fun `music max duration should increase with age`() {
        val under5 = MusicFilterConfig.ageConfigs[AgeGroup.UNDER_5]!!.maxDuration
        val under8 = MusicFilterConfig.ageConfigs[AgeGroup.UNDER_8]!!.maxDuration
        val under13 = MusicFilterConfig.ageConfigs[AgeGroup.UNDER_13]!!.maxDuration
        val under16 = MusicFilterConfig.ageConfigs[AgeGroup.UNDER_16]!!.maxDuration

        assertTrue("UNDER_5 should have shortest duration", under5 <= under8)
        assertTrue("UNDER_8 should have shorter duration than UNDER_13", under8 <= under13)
        assertTrue("UNDER_13 should have shorter duration than UNDER_16", under13 <= under16)
    }

    // ==================== Whitelisted Artists Tests ====================

    @Test
    fun `whitelistedArtists should have exactly 4 entries`() {
        assertEquals(4, MusicFilterConfig.whitelistedArtists.size)
    }

    @Test
    fun `whitelistedArtists should contain only simplified age groups`() {
        val keys = MusicFilterConfig.whitelistedArtists.keys
        assertTrue(keys.contains(AgeGroup.UNDER_5))
        assertTrue(keys.contains(AgeGroup.UNDER_8))
        assertTrue(keys.contains(AgeGroup.UNDER_13))
        assertTrue(keys.contains(AgeGroup.UNDER_16))
    }

    @Test
    fun `UNDER_5 should have kid-focused artists`() {
        val artists = MusicFilterConfig.whitelistedArtists[AgeGroup.UNDER_5]
        assertNotNull(artists)
        assertTrue(artists!!.isNotEmpty())
        // Should contain CoComelon
        assertTrue(artists.contains("UCBnZ16ahKA2DZ_T5W0FPUXg"))
    }

    // ==================== Trusted Artist Names Tests ====================

    @Test
    fun `trustedArtistNames should contain known safe artists`() {
        val artists = MusicFilterConfig.trustedArtistNames
        assertTrue(artists.contains("cocomelon"))
        assertTrue(artists.contains("pinkfong"))
        assertTrue(artists.contains("disney"))
        assertTrue(artists.contains("kidz bop"))
        assertTrue(artists.contains("sesame street"))
    }

    // ==================== Blocklist Keywords Tests ====================

    @Test
    fun `music blocklistKeywords should have exactly 4 entries`() {
        assertEquals(4, MusicFilterConfig.blocklistKeywords.size)
    }

    @Test
    fun `music blocklistKeywords should contain only simplified age groups`() {
        val keys = MusicFilterConfig.blocklistKeywords.keys
        assertTrue(keys.contains(AgeGroup.UNDER_5))
        assertTrue(keys.contains(AgeGroup.UNDER_8))
        assertTrue(keys.contains(AgeGroup.UNDER_13))
        assertTrue(keys.contains(AgeGroup.UNDER_16))
    }

    @Test
    fun `music blocklistKeywords should NOT contain removed age groups`() {
        val keys = MusicFilterConfig.blocklistKeywords.keys.map { it.name }
        assertFalse("UNDER_10 should not exist", keys.contains("UNDER_10"))
        assertFalse("UNDER_12 should not exist", keys.contains("UNDER_12"))
        assertFalse("UNDER_14 should not exist", keys.contains("UNDER_14"))
    }

    @Test
    fun `UNDER_5 music should block violence and adult keywords`() {
        val keywords = MusicFilterConfig.blocklistKeywords[AgeGroup.UNDER_5]!!
        assertTrue(keywords.contains("kill"))
        assertTrue(keywords.contains("death"))
        assertTrue(keywords.contains("sex"))
        assertTrue(keywords.contains("drug"))
        assertTrue(keywords.contains("fuck"))
    }

    @Test
    fun `all music age groups should block explicit content keywords`() {
        for ((ageGroup, keywords) in MusicFilterConfig.blocklistKeywords) {
            val hasExplicitBlock = keywords.contains("explicit") ||
                    keywords.contains("18+") ||
                    keywords.contains("adult")
            assertTrue("$ageGroup should block explicit keywords", hasExplicitBlock)
        }
    }

    @Test
    fun `UNDER_5 music blocklist should be most restrictive`() {
        val under5Keywords = MusicFilterConfig.blocklistKeywords[AgeGroup.UNDER_5]!!
        val under16Keywords = MusicFilterConfig.blocklistKeywords[AgeGroup.UNDER_16]!!

        assertTrue(
            "UNDER_5 should have more blocked keywords than UNDER_16",
            under5Keywords.size >= under16Keywords.size
        )
    }

    // ==================== Safe Music Keywords Tests ====================

    @Test
    fun `safeMusicKeywords should contain kid-friendly terms`() {
        val keywords = MusicFilterConfig.safeMusicKeywords
        assertTrue(keywords.contains("kids"))
        assertTrue(keywords.contains("nursery"))
        assertTrue(keywords.contains("lullaby"))
        assertTrue(keywords.contains("disney"))
        assertTrue(keywords.contains("cocomelon"))
    }

    @Test
    fun `safeMusicKeywords should contain classical and calm music terms`() {
        val keywords = MusicFilterConfig.safeMusicKeywords
        assertTrue(keywords.contains("classical"))
        assertTrue(keywords.contains("relaxing"))
        assertTrue(keywords.contains("calm"))
        assertTrue(keywords.contains("sleep"))
    }

    // ==================== Inappropriate Music Themes Tests ====================

    @Test
    fun `inappropriateMusicThemes should contain relationship themes`() {
        val themes = MusicFilterConfig.inappropriateMusicThemes
        assertTrue(themes.contains("love song"))
        assertTrue(themes.contains("romance"))
        assertTrue(themes.contains("heartbreak"))
        assertTrue(themes.contains("breakup"))
    }

    @Test
    fun `inappropriateMusicThemes should contain party themes`() {
        val themes = MusicFilterConfig.inappropriateMusicThemes
        assertTrue(themes.contains("club"))
        assertTrue(themes.contains("party anthem"))
        assertTrue(themes.contains("twerk"))
    }

    // ==================== Artist Reputation Tests ====================

    @Test
    fun `artistReputation should have CoComelon with highest score`() {
        val cocomelon = MusicFilterConfig.artistReputation["UCBnZ16ahKA2DZ_T5W0FPUXg"]
        assertNotNull(cocomelon)
        assertEquals(100, cocomelon)
    }

    @Test
    fun `artistReputation scores should be between 0 and 100`() {
        for ((_, score) in MusicFilterConfig.artistReputation) {
            assertTrue(score in 0..100)
        }
    }

    // ==================== Integration Tests ====================

    @Test
    fun `getting config for any AgeGroup should not throw`() {
        for (ageGroup in AgeGroup.entries) {
            val config = MusicFilterConfig.ageConfigs[ageGroup]
            assertNotNull("Config should exist for $ageGroup", config)
        }
    }

    @Test
    fun `getting artists for any AgeGroup should not throw`() {
        for (ageGroup in AgeGroup.entries) {
            val artists = MusicFilterConfig.whitelistedArtists[ageGroup]
            assertNotNull("Artists should exist for $ageGroup", artists)
        }
    }

    @Test
    fun `getting blocklist for any AgeGroup should not throw`() {
        for (ageGroup in AgeGroup.entries) {
            val blocklist = MusicFilterConfig.blocklistKeywords[ageGroup]
            assertNotNull("Blocklist should exist for $ageGroup", blocklist)
        }
    }
}
