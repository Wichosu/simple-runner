package com.wichosu.simplerunner.tracking

import com.wichosu.simplerunner.data.RunStore
import com.wichosu.simplerunner.data.local.RunEntity
import com.wichosu.simplerunner.data.local.TrackPointEntity
import com.wichosu.simplerunner.domain.RecordedPoint
import com.wichosu.simplerunner.domain.RunSnapshot
import com.wichosu.simplerunner.domain.RunState
import com.wichosu.simplerunner.domain.RunTracker
import com.wichosu.simplerunner.domain.TrackPoint
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Application-scoped owner of the current run.
 *
 * Lives in [com.wichosu.simplerunner.data.AppContainer] rather than in the service so a
 * configuration change, a screen being destroyed, or the service being torn down cannot
 * lose the in-memory run. Only [RunTrackingService] mutates it; every UI surface observes
 * [state] and asks the service to act. That keeps all mutations on one thread and gives
 * one place to reason about pause/resume and persistence ordering.
 *
 * Persistence runs on a single-thread dispatcher, so writes execute in the order they
 * were requested. In particular the run row is always inserted before its first point,
 * which the foreign key requires.
 */
class RunSession(
    private val store: RunStore,
    private val scope: CoroutineScope,
    private val persistenceDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickMillis: Long = 1_000L,
) {
    private val tracker = RunTracker(clock)
    private val _state = MutableStateFlow(RunSnapshot.Idle)
    val state: StateFlow<RunSnapshot> = _state.asStateFlow()

    private val _currentRunId = MutableStateFlow<String?>(null)

    /**
     * The run the live map should draw, or null when nothing is active. The map reads
     * points from the database rather than from [state], so this is the id it needs.
     */
    val currentRunId: StateFlow<String?> = _currentRunId.asStateFlow()

    private var runId: String?
        get() = _currentRunId.value
        set(value) {
            _currentRunId.value = value
        }

    private var ticker: Job? = null
    private var lastWrite: Job? = null

    /** True while a run exists in memory, including a paused one. */
    val isActive: Boolean get() = _state.value.state != RunState.IDLE

    fun start() {
        // A finished run can still be in memory: [RunTrackingService] resets it after
        // the closing write lands, and that coroutine dies with the service if the
        // system tears it down first. Recover here, or the start is silently dropped
        // and the screen keeps showing a run that is over.
        if (_state.value.state == RunState.FINISHED) reset()

        if (_state.value.state != RunState.IDLE) return

        val now = clock()
        val id = UUID.randomUUID().toString()
        runId = id

        // [RunTracker.start] refuses to run unless the tracker is idle, so a tracker
        // left behind by an earlier run would persist that run's stats under the new
        // id. Clearing it first makes a cloned run impossible.
        tracker.reset()
        tracker.start(now)
        _state.value = tracker.snapshot(now)
        persistRun()
        startTicker()
    }

    /**
     * Brings a run that outlived the process back into memory. [maxSeq] is the highest
     * sequence number already in the database for this run.
     */
    fun adopt(entity: RunEntity, maxSeq: Int, resumeNow: Boolean) {
        if (_state.value.state != RunState.IDLE) return

        runId = entity.id
        tracker.restorePaused(
            startedAt = entity.startedAt,
            movingMillis = entity.movingMillis,
            distanceM = entity.distanceM,
            maxSpeedMps = entity.maxSpeedMps,
            segment = (entity.segmentCount - 1).coerceAtLeast(0),
            seq = maxSeq,
        )
        _state.value = tracker.snapshot()

        if (resumeNow) {
            resume()
        } else {
            persistRun()
        }
    }

    fun pause() {
        if (_state.value.state != RunState.RECORDING) return

        ticker?.cancel()
        ticker = null
        tracker.pause(clock())
        _state.value = tracker.snapshot()
        persistRun()
    }

    fun resume() {
        if (_state.value.state != RunState.PAUSED) return

        tracker.resume(clock())
        _state.value = tracker.snapshot()
        persistRun()
        startTicker()
    }

    fun finish() {
        val current = _state.value
        if (current.state == RunState.IDLE || current.state == RunState.FINISHED) return

        ticker?.cancel()
        ticker = null
        tracker.finish(clock())
        _state.value = tracker.snapshot()
        persistRun()
    }

    /** Clears the finished run so the next one starts from a clean slate. */
    fun reset() {
        if (_state.value.state == RunState.RECORDING || _state.value.state == RunState.PAUSED) return

        ticker?.cancel()
        ticker = null
        runId = null
        tracker.reset()
        _state.value = RunSnapshot.Idle
    }

    /**
     * Drops whatever run is in memory without writing to the database, for when the
     * user deletes the row itself. Unlike [reset] this also abandons a recording run,
     * which is why only the service calls it (after stopping location updates).
     */
    fun forget() {
        ticker?.cancel()
        ticker = null
        runId = null
        tracker.reset()
        _state.value = RunSnapshot.Idle
    }

    fun addPoint(point: TrackPoint) {
        val recorded = tracker.addPoint(point) ?: return

        _state.value = tracker.snapshot()
        persistPoint(recorded)
        persistRun()
    }

    /** Waits for every write requested so far. Used before stopping the service. */
    suspend fun flush() {
        lastWrite?.join()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && _state.value.state == RunState.RECORDING) {
                delay(tickMillis)
                if (!isActive || _state.value.state != RunState.RECORDING) break
                _state.value = tracker.snapshot()
                persistRun()
            }
        }
    }

    private fun persistRun() {
        val id = runId ?: return
        val snapshot = _state.value
        val recording = snapshot.state == RunState.RECORDING

        val entity = RunEntity(
            id = id,
            startedAt = snapshot.startedAt,
            endedAt = snapshot.endedAt,
            movingMillis = snapshot.movingMillis,
            // Only meaningful as "the run was recording when this row was written";
            // recovery restores the run paused and uses movingMillis as stored.
            lastResumeAt = if (recording) clock() else null,
            distanceM = snapshot.distanceM,
            maxSpeedMps = snapshot.maxSpeedMps,
            segmentCount = snapshot.segmentCount,
            createdAt = snapshot.startedAt,
        )
        persist { save(entity) }
    }

    private fun persistPoint(recorded: RecordedPoint) {
        val id = runId ?: return

        val entity = TrackPointEntity(
            runId = id,
            segment = recorded.segment,
            seq = recorded.seq,
            lat = recorded.point.lat,
            lon = recorded.point.lon,
            accuracyM = recorded.point.accuracyM,
            speedMps = recorded.point.speedMps,
            elevationM = recorded.point.elevationM,
            recordedAt = recorded.point.recordedAt,
        )
        persist {
            addPoint(entity)
        }
    }

    private fun persist(block: suspend RunStore.() -> Unit) {
        lastWrite = scope.launch(persistenceDispatcher) {
            // A failed write must never take the tracking process down mid-run.
            runCatching { store.block() }
                .onFailure { it.printStackTrace() }
        }
    }
}
