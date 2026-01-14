package com.zimbabeats.core.domain.filter

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AgeGroup enum
 * Tests the simplified age groups (UNDER_5, UNDER_8, UNDER_13, UNDER_16)
 * and legacy code mappings
 */
class AgeGroupTest {

    // ==================== Enum Values Test ====================

    @Test
    fun `AgeGroup should have exactly 4 values`() {
        val values = AgeGroup.entries
        assertEquals(4, values.size)
    }

    @Test
    fun `AgeGroup should contain correct values`() {
        val values = AgeGroup.entries.map { it.name }
        assertTrue(values.contains("UNDER_5"))
        assertTrue(values.contains("UNDER_8"))
        assertTrue(values.contains("UNDER_13"))
        assertTrue(values.contains("UNDER_16"))
    }

    @Test
    fun `AgeGroup should NOT contain removed values`() {
        val values = AgeGroup.entries.map { it.name }
        assertFalse("UNDER_10 should be removed", values.contains("UNDER_10"))
        assertFalse("UNDER_12 should be removed", values.contains("UNDER_12"))
        assertFalse("UNDER_14 should be removed", values.contains("UNDER_14"))
    }

    // ==================== Max Age Test ====================

    @Test
    fun `UNDER_5 should have maxAge 5`() {
        assertEquals(5, AgeGroup.UNDER_5.maxAge)
    }

    @Test
    fun `UNDER_8 should have maxAge 8`() {
        assertEquals(8, AgeGroup.UNDER_8.maxAge)
    }

    @Test
    fun `UNDER_13 should have maxAge 13`() {
        assertEquals(13, AgeGroup.UNDER_13.maxAge)
    }

    @Test
    fun `UNDER_16 should have maxAge 16`() {
        assertEquals(16, AgeGroup.UNDER_16.maxAge)
    }

    // ==================== Labels Test ====================

    @Test
    fun `AgeGroup labels should be correct`() {
        assertEquals("Under 5", AgeGroup.UNDER_5.label)
        assertEquals("Under 8", AgeGroup.UNDER_8.label)
        assertEquals("Under 13", AgeGroup.UNDER_13.label)
        assertEquals("Under 16", AgeGroup.UNDER_16.label)
    }

    // ==================== fromAge() Tests ====================

    @Test
    fun `fromAge should return UNDER_5 for ages 0-4`() {
        assertEquals(AgeGroup.UNDER_5, AgeGroup.fromAge(0))
        assertEquals(AgeGroup.UNDER_5, AgeGroup.fromAge(1))
        assertEquals(AgeGroup.UNDER_5, AgeGroup.fromAge(2))
        assertEquals(AgeGroup.UNDER_5, AgeGroup.fromAge(3))
        assertEquals(AgeGroup.UNDER_5, AgeGroup.fromAge(4))
    }

    @Test
    fun `fromAge should return UNDER_8 for ages 5-7`() {
        assertEquals(AgeGroup.UNDER_8, AgeGroup.fromAge(5))
        assertEquals(AgeGroup.UNDER_8, AgeGroup.fromAge(6))
        assertEquals(AgeGroup.UNDER_8, AgeGroup.fromAge(7))
    }

    @Test
    fun `fromAge should return UNDER_13 for ages 8-12`() {
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromAge(8))
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromAge(9))
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromAge(10))
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromAge(11))
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromAge(12))
    }

    @Test
    fun `fromAge should return UNDER_16 for ages 13 and above`() {
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromAge(13))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromAge(14))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromAge(15))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromAge(16))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromAge(17))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromAge(18))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromAge(100))
    }

    @Test
    fun `fromAge boundary conditions`() {
        // Age 4 -> UNDER_5
        assertEquals(AgeGroup.UNDER_5, AgeGroup.fromAge(4))
        // Age 5 -> UNDER_8 (boundary)
        assertEquals(AgeGroup.UNDER_8, AgeGroup.fromAge(5))

        // Age 7 -> UNDER_8
        assertEquals(AgeGroup.UNDER_8, AgeGroup.fromAge(7))
        // Age 8 -> UNDER_13 (boundary)
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromAge(8))

        // Age 12 -> UNDER_13
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromAge(12))
        // Age 13 -> UNDER_16 (boundary)
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromAge(13))
    }

    // ==================== fromCode() Tests ====================

    @Test
    fun `fromCode should return correct AgeGroup for valid codes`() {
        assertEquals(AgeGroup.UNDER_5, AgeGroup.fromCode("UNDER_5"))
        assertEquals(AgeGroup.UNDER_8, AgeGroup.fromCode("UNDER_8"))
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromCode("UNDER_13"))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromCode("UNDER_16"))
    }

    @Test
    fun `fromCode should return UNDER_16 for unknown codes`() {
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromCode("UNKNOWN"))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromCode(""))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromCode("INVALID"))
    }

    // ==================== Legacy Mapping Tests ====================

    @Test
    fun `fromCode should map legacy UNDER_10 to UNDER_8`() {
        assertEquals(AgeGroup.UNDER_8, AgeGroup.fromCode("UNDER_10"))
    }

    @Test
    fun `fromCode should map legacy UNDER_12 to UNDER_13`() {
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromCode("UNDER_12"))
    }

    @Test
    fun `fromCode should map legacy UNDER_14 to UNDER_13`() {
        assertEquals(AgeGroup.UNDER_13, AgeGroup.fromCode("UNDER_14"))
    }

    @Test
    fun `legacy mappings preserve restrictiveness`() {
        // UNDER_10 maps to UNDER_8 (more restrictive - safer)
        val under10Mapped = AgeGroup.fromCode("UNDER_10")
        assertTrue(under10Mapped.maxAge <= 10)

        // UNDER_12 maps to UNDER_13 (appropriate age range)
        val under12Mapped = AgeGroup.fromCode("UNDER_12")
        assertTrue(under12Mapped.maxAge >= 12)

        // UNDER_14 maps to UNDER_13 (more restrictive - safer)
        val under14Mapped = AgeGroup.fromCode("UNDER_14")
        assertTrue(under14Mapped.maxAge <= 14)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `fromAge handles negative ages`() {
        // Negative ages should map to youngest group
        assertEquals(AgeGroup.UNDER_5, AgeGroup.fromAge(-1))
        assertEquals(AgeGroup.UNDER_5, AgeGroup.fromAge(-100))
    }

    @Test
    fun `fromCode is case sensitive`() {
        // Lowercase should not match
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromCode("under_5"))
        assertEquals(AgeGroup.UNDER_16, AgeGroup.fromCode("Under_8"))
    }
}
