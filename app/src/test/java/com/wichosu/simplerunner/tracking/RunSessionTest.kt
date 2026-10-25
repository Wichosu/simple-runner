package com.wichosu.simplerunner.tracking

import com.wichosu.simplerunner.data.RunStore
import com.wichosu.simplerunner.data.local.RunEntity
import com.wichosu.simplerunner.data.local.TrackPointEntity
import com.wichosu.simplerunner.domain.RunState
import com.wichosu.simplerunner.domain.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSessionTest {

    private var now = 1_000_000L

    private class FakeStore : RunStore {
        val runs = mutableMapOf<String, RunEntity>()
        val points = mutableListOf<TrackPointEntity>()

        override suspend fun activeRun(): RunEntity? =
            runs.values.firstOrNull { it.endedAt == null }

        override suspend fun save(run: RunEntity) {
            runs[run.id] = run
        }

        override suspend fun addPoint(point: TrackPointEntity) {
            points += point
        }

        override suspend fun maxSeq(runId: String): Int =
            points.filter { it.runId == runId }.maxOfOrNull { it.seq } ?: 0

        override suspend fun deleteRun(id: String) {
            runs.remove(id)
            points.removeAll { it.runId == id }
        }
    }

    /**
     * Unconfined keeps the test free of coroutine-test plumbing: persistence launches
     * run before the call returns, and the ticker never fires at a one-hour interval.
     */
    private fun session(store: FakeStore) = RunSession(
        store = store,
        scope = CoroutineScope(Dispatchers.Unconfined),
        persistenceDispatcher = Dispatchers.Unconfined,
        clock = { now },
        tickMillis = 3_600_000L,
    )

    private fun point(lat: Double, lon: Double) = TrackPoint(
        lat = lat,
        lon = lon,
        accuracyM = 5f,
        recordedAt = now,
    )

    @Test
    fun `start persists an open run`() {
        val store = FakeStore()
        val session = session(store)

        session.start()

        assertEquals(RunState.RECORDING, session.state.value.state)
        val row = store.runs.values.single()
        assertNull(row.endedAt)
        assertEquals(session.state.value.startedAt, row.startedAt)
        assertEquals(0.0, row.distanceM, 0.0)
    }

    @Test
    fun `accepted points are persisted and update the run row`() {
        val store = FakeStore()
        val session = session(store)
        session.start()

        now += 1_000
        session.addPoint(point(48.0, 11.0))
        now += 1_000
        session.addPoint(point(48.0001, 11.0))

        assertEquals(2, store.points.size)
        assertEquals(1, store.points.first().seq)
        assertEquals(2, store.points.last().seq)

        val row = store.runs.values.single()
        assertEquals(11.12, row.distanceM, 0.2)
        assertEquals(1, row.segmentCount)
    }

    @Test
    fun `points arriving while paused are dropped`() {
        val store = FakeStore()
        val session = session(store)
        session.start()

        now += 1_000
        session.addPoint(point(48.0, 11.0))
        session.pause()
        val storedBefore = store.points.size

        now += 5_000
        session.addPoint(point(48.0005, 11.0))

        assertEquals(storedBefore, store.points.size)
        assertEquals(RunState.PAUSED, session.state.value.state)
    }

    @Test
    fun `finish closes the row and reset returns to idle`() {
        val store = FakeStore()
        val session = session(store)
        session.start()

        now += 60_000
        session.finish()
        runBlocking { session.flush() }

        val row = store.runs.values.single()
        assertNotNull(row.endedAt)
        assertEquals(60_000L, row.movingMillis)
        assertEquals(RunState.FINISHED, session.state.value.state)

        session.reset()
        assertEquals(RunState.IDLE, session.state.value.state)
    }

    @Test
    fun `starting a run after finishing one begins from zero`() {
        val store = FakeStore()
        val session = session(store)
        session.start()

        now += 1_000
        session.addPoint(point(48.0, 11.0))
        now += 1_000
        session.addPoint(point(48.0001, 11.0))
        now += 60_000
        session.finish()
        runBlocking { session.flush() }
        val firstRunId = store.runs.keys.single()
        session.reset()

        now += 1_000
        session.start()

        assertEquals(RunState.RECORDING, session.state.value.state)
        assertEquals(0.0, session.state.value.distanceM, 0.0)
        assertEquals(0L, session.state.value.movingMillis)
        assertEquals(1, session.state.value.segmentCount)
        assertEquals(2, store.runs.size)

        // The new run must actually record; the old bug left the tracker FINISHED,
        // which produced a clone of the previous run and dropped every point.
        now += 1_000
        session.addPoint(point(48.5, 11.5))
        now += 1_000
        session.addPoint(point(48.5001, 11.5))

        val secondRunId = store.runs.keys.single { it != firstRunId }
        assertEquals(2, store.points.count { it.runId == secondRunId })
        assertEquals(11.12, store.runs.getValue(secondRunId).distanceM, 0.2)
    }

    @Test
    fun `start after a finish that was never reset begins a clean run`() {
        val store = FakeStore()
        val session = session(store)
        session.start()

        now += 1_000
        session.addPoint(point(48.0, 11.0))
        now += 1_000
        session.addPoint(point(48.0001, 11.0))
        now += 60_000
        session.finish()
        runBlocking { session.flush() }

        // The service resets a finished run, but that coroutine dies with it. A start
        // must still work and must not inherit the finished run's stats.
        assertEquals(RunState.FINISHED, session.state.value.state)
        val firstRunId = store.runs.keys.single()
        val firstRun = store.runs.getValue(firstRunId)

        now += 1_000
        session.start()

        assertEquals(RunState.RECORDING, session.state.value.state)
        assertEquals(0.0, session.state.value.distanceM, 0.0)
        assertEquals(0L, session.state.value.movingMillis)
        assertEquals(2, store.runs.size)
        assertNotNull(store.runs.getValue(firstRunId).endedAt)
        assertEquals(firstRun.movingMillis, store.runs.getValue(firstRunId).movingMillis)

        now += 1_000
        session.addPoint(point(49.0, 12.0))
        now += 1_000
        session.addPoint(point(49.0001, 12.0))

        val secondRunId = store.runs.keys.single { it != firstRunId }
        assertEquals(2, store.points.count { it.runId == secondRunId })
        assertEquals(11.12, store.runs.getValue(secondRunId).distanceM, 0.2)
    }

    @Test
    fun `starting twice only creates one run`() {
        val store = FakeStore()
        val session = session(store)

        now += 1_000
        session.start()
        now += 1_000
        session.start()

        assertEquals(1, store.runs.size)
        assertEquals(RunState.RECORDING, session.state.value.state)
        assertEquals(0.0, session.state.value.distanceM, 0.0)
    }

    @Test
    fun `forget drops the in-memory run and leaves the database alone`() {
        val store = FakeStore()
        val session = session(store)
        session.start()

        now += 1_000
        session.addPoint(point(48.0, 11.0))
        session.pause()
        runBlocking { session.flush() }

        session.forget()

        assertEquals(RunState.IDLE, session.state.value.state)
        assertNull(session.currentRunId.value)
        assertEquals(1, store.runs.size)
    }

    @Test
    fun `adopted run restores stats and continues its sequence`() {
        val store = FakeStore()
        val entity = RunEntity(
            id = "run-1",
            startedAt = 500_000,
            endedAt = null,
            movingMillis = 120_000,
            lastResumeAt = null,
            distanceM = 800.0,
            maxSpeedMps = 3.4,
            segmentCount = 2,
            createdAt = 500_000,
        )
        store.runs[entity.id] = entity
        store.points += TrackPointEntity(
            runId = entity.id,
            segment = 1,
            seq = 17,
            lat = 48.0,
            lon = 11.0,
            accuracyM = 5f,
            speedMps = null,
            elevationM = null,
            recordedAt = 900_000,
        )

        val session = session(store)
        session.adopt(entity, maxSeq = 17, resumeNow = false)

        assertEquals(RunState.PAUSED, session.state.value.state)
        assertEquals(800.0, session.state.value.distanceM, 0.0)
        assertEquals(2, session.state.value.segmentCount)

        now += 60_000
        session.resume()
        now += 10_000
        session.addPoint(point(48.0, 11.0))

        assertEquals(130_000L, session.state.value.movingMillis)

        val stored = store.points.last()
        assertEquals(18, stored.seq)
        assertEquals(2, stored.segment)
        assertTrue(stored.runId == entity.id)
    }
}
