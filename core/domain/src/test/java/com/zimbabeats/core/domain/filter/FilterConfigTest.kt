package com.zimbabeats.core.domain.filter

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FilterConfig
 * Tests the simplified age group configurations (UNDER_5, UNDER_8, UNDER_13, UNDER_16)
 */
class FilterConfigTest {

    // ==================== Age Configs Tests ====================

    @Test
    fun `ageConfigs should have exactly 4 entries`() {
        assertEquals(4, FilterConfig.ageConfigs.size)
    }

    @Test
    fun `ageConfigs should contain only simplified age groups`() {
        val keys = FilterConfig.ageConfigs.keys
        assertTrue(keys.contains(AgeGroup.UNDER_5))
        assertTrue(keys.contains(AgeGroup.UNDER_8))
        assertTrue(keys.contains(AgeGroup.UNDER_13))
        assertTrue(keys.contains(AgeGroup.UNDER_16))
    }

    @Test
    fun `ageConfigs should NOT contain removed age groups`() {
        val keys = FilterConfig.ageConfigs.keys.map { it.name }
        assertFalse("UNDER_10 should not exist", keys.contains("UNDER_10"))
        assertFalse("UNDER_12 should not exist", keys.contains("UNDER_12"))
        assertFalse("UNDER_14 should not exist", keys.contains("UNDER_14"))
    }

    @Test
    fun `UNDER_5 config should have strict mode enabled`() {
        val config = FilterConfig.ageConfigs[AgeGroup.UNDER_5]
        assertNotNull(config)
        assertTrue(config!!.strictMode)
    }

    @Test
    fun `UNDER_8 config should have strict mode enabled`() {
        val config = FilterConfig.ageConfigs[AgeGroup.UNDER_8]
        assertNotNull(config)
        assertTrue(config!!.strictMode)
    }

    @Test
    fun `UNDER_13 config should have strict mode disabled`() {
        val config = FilterConfig.ageConfigs[AgeGroup.UNDER_13]
        assertNotNull(config)
        assertFalse(config!!.strictMode)
    }

    @Test
    fun `UNDER_16 config should have strict mode disabled`() {
        val config = FilterConfig.ageConfigs[AgeGroup.UNDER_16]
        assertNotNull(config)
        assertFalse(config!!.strictMode)
    }

    @Test
    fun `max duration should increase with age`() {
        val under5 = FilterConfig.ageConfigs[AgeGroup.UNDER_5]!!.maxDuration
        val under8 = FilterConfig.ageConfigs[AgeGroup.UNDER_8]!!.maxDuration
        val under13 = FilterConfig.ageConfigs[AgeGroup.UNDER_13]!!.maxDuration
        val under16 = FilterConfig.ageConfigs[AgeGroup.UNDER_16]!!.maxDuration

        assertTrue("UNDER_5 should have shortest duration", under5 <= under8)
        assertTrue("UNDER_8 should have shorter duration than UNDER_13", under8 <= under13)
        assertTrue("UNDER_13 should have shorter duration than UNDER_16", under13 <= under16)
    }

    // ==================== Whitelisted Channels Tests ====================

    @Test
    fun `whitelistedChannels should have exactly 4 entries`() {
        assertEquals(4, FilterConfig.whitelistedChannels.size)
    }

    @Test
    fun `whitelistedChannels should contain only simplified age groups`() {
        val keys = FilterConfig.whitelistedChannels.keys
        assertTrue(keys.contains(AgeGroup.UNDER_5))
        assertTrue(keys.contains(AgeGroup.UNDER_8))
        assertTrue(keys.contains(AgeGroup.UNDER_13))
        assertTrue(keys.contains(AgeGroup.UNDER_16))
    }

    @Test
    fun `UNDER_5 should have kid-focused channels`() {
        val channels = FilterConfig.whitelistedChannels[AgeGroup.UNDER_5]
        assertNotNull(channels)
        assertTrue(channels!!.isNotEmpty())
        // Should contain CoComelon
        assertTrue(channels.contains("UCBnZ16ahKA2DZ_T5W0FPUXg"))
    }

    @Test
    fun `UNDER_8 should have more channels than UNDER_5`() {
        val under5Channels = FilterConfig.whitelistedChannels[AgeGroup.UNDER_5]!!
        val under8Channels = FilterConfig.whitelistedChannels[AgeGroup.UNDER_8]!!

        assertTrue(under8Channels.size >= under5Channels.size)
    }

    // ==================== Blocklist Keywords Tests ====================

    @Test
    fun `blocklistKeywords should have exactly 4 entries`() {
        assertEquals(4, FilterConfig.blocklistKeywords.size)
    }

    @Test
    fun `blocklistKeywords should contain only simplified age groups`() {
        val keys = FilterConfig.blocklistKeywords.keys
        assertTrue(keys.contains(AgeGroup.UNDER_5))
        assertTrue(keys.contains(AgeGroup.UNDER_8))
        assertTrue(keys.contains(AgeGroup.UNDER_13))
        assertTrue(keys.contains(AgeGroup.UNDER_16))
    }

    @Test
    fun `UNDER_5 should have most restrictive blocklist`() {
        val under5Keywords = FilterConfig.blocklistKeywords[AgeGroup.UNDER_5]!!
        val under16Keywords = FilterConfig.blocklistKeywords[AgeGroup.UNDER_16]!!

        assertTrue(
            "UNDER_5 should have more blocked keywords than UNDER_16",
            under5Keywords.size >= under16Keywords.size
        )
    }

    @Test
    fun `all age groups should block explicit content keywords`() {
        for ((ageGroup, keywords) in FilterConfig.blocklistKeywords) {
            assertTrue(
                "$ageGroup should block 'sex'",
                keywords.contains("sex") || keywords.contains("porn")
            )
            assertTrue(
                "$ageGroup should block '18+'",
                keywords.contains("18+")
            )
        }
    }

    @Test
    fun `UNDER_5 should block violence keywords`() {
        val keywords = FilterConfig.blocklistKeywords[AgeGroup.UNDER_5]!!
        assertTrue(keywords.contains("kill"))
        assertTrue(keywords.contains("death"))
        assertTrue(keywords.contains("blood"))
        assertTrue(keywords.contains("gun"))
        assertTrue(keywords.contains("violence"))
    }

    @Test
    fun `UNDER_5 should block scary keywords`() {
        val keywords = FilterConfig.blocklistKeywords[AgeGroup.UNDER_5]!!
        assertTrue(keywords.contains("scary"))
        assertTrue(keywords.contains("horror"))
        assertTrue(keywords.contains("nightmare"))
        assertTrue(keywords.contains("monster"))
    }

    // ==================== Safe Keywords Tests ====================

    @Test
    fun `safeKeywords should contain kid-friendly terms`() {
        val keywords = FilterConfig.safeKeywords
        assertTrue(keywords.contains("kids"))
        assertTrue(keywords.contains("children"))
        assertTrue(keywords.contains("nursery"))
        assertTrue(keywords.contains("educational"))
        assertTrue(keywords.contains("disney"))
        assertTrue(keywords.contains("cocomelon"))
    }

    // ==================== Suspicious Keywords Tests ====================

    @Test
    fun `suspiciousKeywords should contain Elsagate-style patterns`() {
        val keywords = FilterConfig.suspiciousKeywords
        assertTrue(keywords.contains("surprise egg"))
        assertTrue(keywords.contains("wrong heads"))
        assertTrue(keywords.contains("bad baby"))
        assertTrue(keywords.contains("finger family"))
    }

    // ==================== Strong Safe Indicators Tests ====================

    @Test
    fun `strongSafeIndicators should contain trusted brands`() {
        val indicators = FilterConfig.strongSafeIndicators
        assertTrue(indicators.contains("cocomelon"))
        assertTrue(indicators.contains("pinkfong"))
        assertTrue(indicators.contains("sesame street"))
        assertTrue(indicators.contains("pbs kids"))
        assertTrue(indicators.contains("disney junior"))
    }

    // ==================== Trusted Channel Names Tests ====================

    @Test
    fun `trustedChannelNames should contain known safe channels`() {
        val names = FilterConfig.trustedChannelNames
        assertTrue(names.contains("cocomelon"))
        assertTrue(names.contains("pinkfong"))
        assertTrue(names.contains("blippi"))
        assertTrue(names.contains("sesame street"))
        assertTrue(names.contains("pbs kids"))
    }

    // ==================== Channel Reputation Tests ====================

    @Test
    fun `channelReputation should have CoComelon with highest score`() {
        val cocomelon = FilterConfig.channelReputation["UCBnZ16ahKA2DZ_T5W0FPUXg"]
        assertNotNull(cocomelon)
        assertEquals(100, cocomelon)
    }

    @Test
    fun `channelReputation scores should be between 0 and 100`() {
        for ((_, score) in FilterConfig.channelReputation) {
            assertTrue(score in 0..100)
        }
    }
}
