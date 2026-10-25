package com.wichosu.simplerunner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    @Test
    fun `duration under an hour is mm ss`() {
        assertEquals("00:00", Formatters.duration(0))
        assertEquals("01:05", Formatters.duration(65_000))
        assertEquals("59:59", Formatters.duration(3_599_000))
    }

    @Test
    fun `duration over an hour is h mm ss`() {
        assertEquals("1:02:05", Formatters.duration(3_725_000))
        assertEquals("12:00:00", Formatters.duration(43_200_000))
    }

    @Test
    fun `negative duration clamps to zero`() {
        assertEquals("00:00", Formatters.duration(-5_000))
    }

    @Test
    fun `pace formats as minutes and seconds`() {
        assertEquals("5:32", Formatters.pace(332.0))
        assertEquals("4:00", Formatters.pace(240.0))
        assertEquals("12:30", Formatters.pace(750.0))
    }

    @Test
    fun `pace is hidden when absent or implausible`() {
        assertEquals("--:--", Formatters.pace(null))
        assertEquals("--:--", Formatters.pace(Double.NaN))
        assertEquals("--:--", Formatters.pace(60.0)) // 1:00 /km is not running
        assertEquals("--:--", Formatters.pace(2_400.0)) // 40:00 /km is standing
    }

    @Test
    fun `distance renders in kilometers with two decimals`() {
        assertEquals("1.23", Formatters.distanceKm(1_234.0))
        assertEquals("0.00", Formatters.distanceKm(4.0))
        assertEquals("10.00", Formatters.distanceKm(10_000.0))
    }

    @Test
    fun `pace from moving time and distance`() {
        val pace = Formatters.paceSecondsPerKm(movingMillis = 332_000, distanceM = 1_000.0)
        assertEquals(332.0, pace!!, 0.001)
    }

    @Test
    fun `pace needs at least a hundred meters`() {
        assertNull(Formatters.paceSecondsPerKm(movingMillis = 30_000, distanceM = 99.0))
    }
}
