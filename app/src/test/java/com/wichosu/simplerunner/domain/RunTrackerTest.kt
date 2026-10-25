package com.wichosu.simplerunner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RunTrackerTest {

    private var now = 1_000_000L

    private fun tracker() = RunTracker(clock = { now }, filter = TrackFilter())

    private fun point(
        lat: Double,
        lon: Double,
        recordedAt: Long = now,
        speedMps: Float? = null,
        accuracyM: Float = 5f,
    ) = TrackPoint(
        lat = lat,
        lon = lon,
        accuracyM = accuracyM,
        recordedAt = recordedAt,
        speedMps = speedMps,
    )

    @Test
    fun `start moves to recording with zeroed stats`() {
        val tracker = tracker()
        tracker.start(now)

        val snapshot = tracker.snapshot(now)
        assertEquals(RunState.RECORDING, snapshot.state)
        assertEquals(0.0, snapshot.distanceM, 0.0)
        assertEquals(0L, snapshot.movingMillis)
    }

    @Test
    fun `reset lets a finished run's tracker start over from zero`() {
        val tracker = tracker()
        tracker.start(now)
        now += 1_000
        tracker.addPoint(point(48.0, 11.0))
        now += 1_000
        tracker.addPoint(point(48.0001, 11.0))
        now += 60_000
        tracker.finish(now)

        tracker.reset()
        assertEquals(RunState.IDLE, tracker.snapshot(now).state)

        now += 5_000
        tracker.start(now)

        val snapshot = tracker.snapshot(now)
        assertEquals(RunState.RECORDING, snapshot.state)
        assertEquals(0.0, snapshot.distanceM, 0.0)
        assertEquals(0L, snapshot.movingMillis)
        assertEquals(1, snapshot.segmentCount)

        now += 1_000
        val recorded = tracker.addPoint(point(48.5, 11.5))
        assertNotNull(recorded)
        assertEquals(1, recorded!!.seq)
        assertEquals(0, recorded.segment)
    }

    @Test
    fun `snapshot reports whether a usable fix has arrived`() {
        val tracker = tracker()
        tracker.start(now)
        assertNull(tracker.snapshot(now).lastFixAt)

        // A poor fix is filtered out, so it must not count as having GPS.
        tracker.addPoint(point(48.0, 11.0, accuracyM = 500f))
        assertNull(tracker.snapshot(now).lastFixAt)

        now += 1_000
        tracker.addPoint(point(48.0, 11.0))
        assertEquals(now, tracker.snapshot(now).lastFixAt)

        tracker.finish(now)
        tracker.reset()
        assertNull(tracker.snapshot(now).lastFixAt)
    }

    @Test
    fun `moving time advances while recording`() {
        val tracker = tracker()
        tracker.start(now)

        now += 65_000

        val snapshot = tracker.snapshot(now)
        assertEquals(65_000L, snapshot.movingMillis)
        assertEquals(65_000L, snapshot.elapsedMillis)
    }

    @Test
    fun `pause freezes moving time but not elapsed time`() {
        val tracker = tracker()
        tracker.start(now)
        now += 30_000
        tracker.pause(now)

        now += 60_000

        val snapshot = tracker.snapshot(now)
        assertEquals(RunState.PAUSED, snapshot.state)
        assertEquals(30_000L, snapshot.movingMillis)
        assertEquals(90_000L, snapshot.elapsedMillis)
    }

    @Test
    fun `pause gap is excluded from moving time`() {
        val tracker = tracker()
        tracker.start(now)
        now += 30_000
        tracker.pause(now)
        now += 60_000
        tracker.resume(now)
        now += 10_000

        assertEquals(40_000L, tracker.snapshot(now).movingMillis)
    }

    @Test
    fun `finish freezes the snapshot`() {
        val tracker = tracker()
        tracker.start(now)
        now += 60_000
        tracker.finish(now)

        now += 60_000

        val snapshot = tracker.snapshot(now)
        assertEquals(RunState.FINISHED, snapshot.state)
        assertEquals(60_000L, snapshot.movingMillis)
        assertEquals(60_000L, snapshot.elapsedMillis)
    }

    @Test
    fun `distance accumulates from accepted points`() {
        val tracker = tracker()
        tracker.start(now)
        tracker.addPoint(point(48.0, 11.0))

        now += 2_000
        tracker.addPoint(point(48.0001, 11.0))

        assertEquals(11.12, tracker.snapshot(now).distanceM, 0.2)
    }

    @Test
    fun `points are ignored while paused`() {
        val tracker = tracker()
        tracker.start(now)
        tracker.pause(now)

        assertNull(tracker.addPoint(point(48.0001, 11.0)))
        assertEquals(0.0, tracker.snapshot(now).distanceM, 0.0)
    }

    @Test
    fun `resume starts a new segment and ignores the gap distance`() {
        val tracker = tracker()
        tracker.start(now)
        tracker.addPoint(point(48.0, 11.0))
        tracker.pause(now)

        now += 600_000
        tracker.resume(now)

        val recorded = tracker.addPoint(point(48.05, 11.0))
        assertNotNull(recorded)
        assertEquals(1, recorded!!.segment)
        assertEquals(0.0, recorded.addedDistanceM, 0.0)
        assertEquals(0.0, tracker.snapshot(now).distanceM, 0.0)
        assertEquals(2, tracker.snapshot(now).segmentCount)
    }

    @Test
    fun `max speed tracks the fastest reported sample`() {
        val tracker = tracker()
        tracker.start(now)
        tracker.addPoint(point(48.0, 11.0, speedMps = 2.5f))

        now += 2_000
        tracker.addPoint(point(48.0001, 11.0, speedMps = 4.1f))

        assertEquals(4.1, tracker.snapshot(now).maxSpeedMps, 0.001)
    }

    @Test
    fun `restore brings an interrupted run back paused with its stats`() {
        val tracker = tracker()
        tracker.restorePaused(
            startedAt = now - 600_000,
            movingMillis = 300_000,
            distanceM = 1_250.0,
            maxSpeedMps = 3.6,
            segment = 1,
            seq = 42,
        )

        val snapshot = tracker.snapshot(now)
        assertEquals(RunState.PAUSED, snapshot.state)
        assertEquals(1_250.0, snapshot.distanceM, 0.0)
        assertEquals(300_000L, snapshot.movingMillis)
        assertEquals(1, snapshot.segment)
    }

    @Test
    fun `restored run continues sequence numbers and opens a new segment`() {
        val tracker = tracker()
        tracker.restorePaused(
            startedAt = now - 60_000,
            movingMillis = 30_000,
            distanceM = 0.0,
            maxSpeedMps = 0.0,
            segment = 1,
            seq = 7,
        )

        now += 10_000
        tracker.resume(now)
        now += 1_000

        val point = tracker.addPoint(point(48.0, 11.0))
        assertNotNull(point)
        assertEquals(8, point!!.seq)
        assertEquals(2, point.segment)
    }

    @Test
    fun `restore is ignored when a run is already in memory`() {
        val tracker = tracker()
        tracker.start(now)
        tracker.restorePaused(now, 1_000, 5.0, 0.0, segment = 3, seq = 9)

        val snapshot = tracker.snapshot(now)
        assertEquals(RunState.RECORDING, snapshot.state)
        assertEquals(0, snapshot.segment)
    }

    @Test
    fun `sequence numbers only advance for accepted points`() {
        val tracker = tracker()
        tracker.start(now)

        val first = tracker.addPoint(point(48.0, 11.0))
        assertEquals(1, first!!.seq)

        // Same location repeatedly: filtered out, so seq must not advance.
        now += 1_000
        assertNull(tracker.addPoint(point(48.0, 11.0)))

        // Bad accuracy: also filtered out.
        now += 1_000
        assertNull(tracker.addPoint(point(48.0001, 11.0, accuracyM = 80f)))

        now += 1_000
        val third = tracker.addPoint(point(48.0001, 11.0))
        assertEquals(2, third!!.seq)
    }
}
