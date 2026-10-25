package com.wichosu.simplerunner.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {

    @Test
    fun `identical points have zero distance`() {
        assertEquals(0.0, Geo.distanceMeters(48.0, 11.0, 48.0, 11.0), 0.0)
    }

    @Test
    fun `one thousandth of a degree of latitude is about 111 m`() {
        val distance = Geo.distanceMeters(48.0, 11.0, 48.001, 11.0)
        assertEquals(111.2, distance, 0.5)
    }

    @Test
    fun `munich to berlin is about 504 km`() {
        val distance = Geo.distanceMeters(48.1372, 11.5756, 52.5200, 13.4050)
        assertEquals(504_000.0, distance, 5_000.0)
    }

    @Test
    fun `antimeridian crossing is a short hop, not half the planet`() {
        val distance = Geo.distanceMeters(0.0, 179.999, 0.0, -179.999)
        assertEquals(222.4, distance, 2.0)
    }

    @Test
    fun `high latitudes shrink longitude distance`() {
        val atEquator = Geo.distanceMeters(0.0, 0.0, 0.0, 0.01)
        val atSixty = Geo.distanceMeters(60.0, 0.0, 60.0, 0.01)
        assertEquals(0.5, atSixty / atEquator, 0.01)
    }
}
