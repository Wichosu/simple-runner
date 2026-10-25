package com.wichosu.simplerunner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TrackFilterTest {

    private fun point(
        lat: Double,
        lon: Double,
        recordedAt: Long,
        accuracyM: Float = 5f,
    ) = TrackPoint(
        lat = lat,
        lon = lon,
        accuracyM = accuracyM,
        recordedAt = recordedAt,
    )

    @Test
    fun `first point is accepted and adds no distance`() {
        val filter = TrackFilter()
        val accepted = filter.accept(point(48.0, 11.0, 0))

        assertNotNull(accepted)
        assertEquals(0.0, accepted!!.addedDistanceM, 0.0)
    }

    @Test
    fun `inaccurate fixes are rejected`() {
        val filter = TrackFilter()
        assertNull(filter.accept(point(48.0, 11.0, 0, accuracyM = 50f)))
    }

    @Test
    fun `samples with a non-advancing timestamp are rejected`() {
        val filter = TrackFilter()
        filter.accept(point(48.0, 11.0, 1_000))
        assertNull(filter.accept(point(48.0001, 11.0, 1_000)))
    }

    @Test
    fun `sub-meter jitter does not accumulate distance`() {
        val filter = TrackFilter()
        filter.accept(point(48.0, 11.0, 0))
        assertNull(filter.accept(point(48.000001, 11.0, 1_000)))
    }

    @Test
    fun `normal movement is accepted and measured`() {
        val filter = TrackFilter()
        filter.accept(point(48.0, 11.0, 0))

        val accepted = filter.accept(point(48.0001, 11.0, 2_000))

        assertNotNull(accepted)
        assertEquals(11.12, accepted!!.addedDistanceM, 0.1)
    }

    @Test
    fun `gps teleport is rejected`() {
        val filter = TrackFilter()
        filter.accept(point(48.0, 11.0, 0))

        // ~1.1 km in one second is a fix jumping, not a runner.
        assertNull(filter.accept(point(48.01, 11.0, 1_000)))
    }

    @Test
    fun `valid movement after a rejected jump is still accepted`() {
        val filter = TrackFilter()
        filter.accept(point(48.0, 11.0, 0))
        assertNull(filter.accept(point(48.01, 11.0, 1_000)))

        val accepted = filter.accept(point(48.0001, 11.0, 3_000))
        assertNotNull(accepted)
        assertEquals(11.12, accepted!!.addedDistanceM, 0.1)
    }

    @Test
    fun `reset starts a new anchor so a pause gap is never measured`() {
        val filter = TrackFilter()
        filter.accept(point(48.0, 11.0, 0))

        filter.reset()

        // The user paused here and resumed 5 km away; that distance must not count.
        val accepted = filter.accept(point(48.05, 11.0, 600_000))
        assertNotNull(accepted)
        assertEquals(0.0, accepted!!.addedDistanceM, 0.0)
    }
}
