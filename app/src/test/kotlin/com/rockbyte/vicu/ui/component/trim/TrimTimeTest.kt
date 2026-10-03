package com.rockbyte.vicu.ui.component.trim

import org.junit.Assert.*
import com.rockbyte.vicu.player.PlayerEffect
import org.junit.Test

class TrimTimeTest {
    @Test fun decimalSecondsAreParsedExactlyAndFormattedWithThreeDigits() {
        assertEquals(12000L, parseTrimTime("12"))
        assertEquals(1500L, parseTrimTime("1.5"))
        assertEquals(1050L, parseTrimTime("1.050"))
        assertEquals("12.000", formatTrimTime(12000))
        assertEquals("1.050", formatTrimTime(1050))
        for (text in listOf("", "1.", ".5", "-1", "1.0000", "NaN", "1e3", "999999999999999999")) {
            assertNull(text, parseTrimTime(text))
        }
    }
    @Test fun endpointsStopBeforeCrossingAndAtVideoBounds() {
        val range = PlayerEffect.Trim(2000, 7000)
        assertEquals(PlayerEffect.Trim(6999, 7000), moveTrimEndpoint(range, true, 9000, 10000))
        assertEquals(PlayerEffect.Trim(0, 7000), moveTrimEndpoint(range, true, -100, 10000))
        assertEquals(PlayerEffect.Trim(2000, 2001), moveTrimEndpoint(range, false, 1000, 10000))
        assertEquals(PlayerEffect.Trim(2000, 10000), moveTrimEndpoint(range, false, 12000, 10000))
    }
    @Test fun invalidRangesHaveSpecificErrorsAndOneMillisecondIsValid() {
        assertEquals(TrimInputError.EMPTY, validateTrimInput("", "2", 10000))
        assertEquals(TrimInputError.FORMAT, validateTrimInput("x", "2", 10000))
        assertEquals(TrimInputError.OUT_OF_BOUNDS, validateTrimInput("0", "11", 10000))
        assertEquals(TrimInputError.ORDER, validateTrimInput("2", "2", 10000))
        assertEquals(TrimInputError.ORDER, validateTrimInput("3", "2", 10000))
        assertNull(validateTrimInput("0", "0.001", 10000))
        assertNull(validateTrimInput("0", "10.000", 10000))
    }
}
