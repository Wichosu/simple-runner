package com.wichosu.simplerunner.domain

enum class RunState { IDLE, RECORDING, PAUSED, FINISHED }

/**
 * Immutable view of a run at a point in time. Moving time excludes pauses; elapsed
 * time does not. Both are needed: pace uses moving time, the detail screen shows elapsed.
 */
data class RunSnapshot(
    val state: RunState,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceM: Double,
    val movingMillis: Long,
    val elapsedMillis: Long,
    val maxSpeedMps: Double,
    val segment: Int,
    /** When the last usable GPS fix arrived, or null if none has been accepted yet. */
    val lastFixAt: Long? = null,
) {
    /** Segments are zero-based internally; the number of track pieces is one more. */
    val segmentCount: Int get() = segment + 1

    companion object {
        val Idle = RunSnapshot(
            state = RunState.IDLE,
            startedAt = 0L,
            endedAt = null,
            distanceM = 0.0,
            movingMillis = 0L,
            elapsedMillis = 0L,
            maxSpeedMps = 0.0,
            segment = 0,
        )
    }
}

/** A GPS sample that survived filtering, tagged with where it belongs in the track. */
data class RecordedPoint(
    val point: TrackPoint,
    val segment: Int,
    val seq: Int,
    val addedDistanceM: Double,
)

/**
 * The run state machine. Deliberately has no Android dependencies: every rule about
 * moving time, pause gaps, and distance accumulation is testable on the JVM.
 *
 * Not thread-safe by design; callers drive it from a single thread (the service's main
 * looper in phase 4, the ViewModel before that).
 */
class RunTracker(
    private val clock: () -> Long = System::currentTimeMillis,
    private val filter: TrackFilter = TrackFilter(),
) {
    private var state = RunState.IDLE
    private var startedAt = 0L
    private var endedAt: Long? = null
    private var accumulatedMovingMillis = 0L
    private var resumedAt: Long? = null
    private var distanceM = 0.0
    private var maxSpeedMps = 0.0
    private var segment = 0
    private var seq = 0
    private var lastFixAt: Long? = null

    fun start(at: Long = clock()) {
        if (state != RunState.IDLE) return

        state = RunState.RECORDING
        startedAt = at
        endedAt = null
        accumulatedMovingMillis = 0L
        resumedAt = at
        distanceM = 0.0
        maxSpeedMps = 0.0
        segment = 0
        seq = 0
        lastFixAt = null
        filter.reset()
    }

    /**
     * Returns the machine to IDLE so a finished run cannot leak into the next one.
     *
     * [start] refuses to run unless the state is IDLE, so whoever owns the tracker must
     * clear a finished run explicitly before beginning another.
     */
    fun reset() {
        state = RunState.IDLE
        startedAt = 0L
        endedAt = null
        accumulatedMovingMillis = 0L
        resumedAt = null
        distanceM = 0.0
        maxSpeedMps = 0.0
        segment = 0
        seq = 0
        lastFixAt = null
        filter.reset()
    }

    fun pause(now: Long = clock()) {
        if (state != RunState.RECORDING) return

        accumulatedMovingMillis = currentMovingMillis(now)
        resumedAt = null
        state = RunState.PAUSED
    }

    fun resume(now: Long = clock()) {
        if (state != RunState.PAUSED) return

        segment++
        filter.reset() // the gap across a pause must never become distance
        resumedAt = now
        state = RunState.RECORDING
    }

    /**
     * Rebuilds a run that was interrupted (process death, force stop) from its last
     * persisted row. The run always comes back paused: the time the app was gone must
     * not be counted as moving time, and there is no GPS track for it.
     *
     * [seq] is the highest sequence number already stored, so new points continue the
     * ordering instead of interleaving with the recovered ones.
     */
    fun restorePaused(
        startedAt: Long,
        movingMillis: Long,
        distanceM: Double,
        maxSpeedMps: Double,
        segment: Int,
        seq: Int,
    ) {
        if (state != RunState.IDLE) return

        state = RunState.PAUSED
        this.startedAt = startedAt
        endedAt = null
        accumulatedMovingMillis = movingMillis.coerceAtLeast(0L)
        resumedAt = null
        this.distanceM = distanceM.coerceAtLeast(0.0)
        this.maxSpeedMps = maxSpeedMps.coerceAtLeast(0.0)
        this.segment = segment.coerceAtLeast(0)
        this.seq = seq.coerceAtLeast(0)
        lastFixAt = null
        filter.reset()
    }

    fun finish(now: Long = clock()) {
        if (state == RunState.IDLE || state == RunState.FINISHED) return

        accumulatedMovingMillis = currentMovingMillis(now)
        resumedAt = null
        endedAt = now
        state = RunState.FINISHED
    }

    /** Feeds one raw sample in. Returns the recorded point only if it was accepted. */
    fun addPoint(point: TrackPoint): RecordedPoint? {
        if (state != RunState.RECORDING) return null

        val accepted = filter.accept(point) ?: return null
        distanceM += accepted.addedDistanceM
        lastFixAt = accepted.point.recordedAt

        point.speedMps
            ?.takeIf { it.isFinite() && it > 0f }
            ?.let { maxSpeedMps = maxOf(maxSpeedMps, it.toDouble()) }

        seq++
        return RecordedPoint(
            point = accepted.point,
            segment = segment,
            seq = seq,
            addedDistanceM = accepted.addedDistanceM,
        )
    }

    fun snapshot(now: Long = clock()): RunSnapshot {
        if (state == RunState.IDLE) return RunSnapshot.Idle

        val end = endedAt ?: now
        return RunSnapshot(
            state = state,
            startedAt = startedAt,
            endedAt = endedAt,
            distanceM = distanceM,
            movingMillis = currentMovingMillis(now),
            elapsedMillis = (end - startedAt).coerceAtLeast(0L),
            maxSpeedMps = maxSpeedMps,
            segment = segment,
            lastFixAt = lastFixAt,
        )
    }

    private fun currentMovingMillis(now: Long): Long {
        val resumed = resumedAt ?: return accumulatedMovingMillis
        return accumulatedMovingMillis + (now - resumed).coerceAtLeast(0L)
    }
}
