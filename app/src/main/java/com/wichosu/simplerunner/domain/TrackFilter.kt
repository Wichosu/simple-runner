package com.wichosu.simplerunner.domain

/**
 * Decides which raw GPS samples become part of a run.
 *
 * Three failure modes are filtered here, and each one corrupts distance if left alone:
 *  - low accuracy fixes (urban canyons) drift tens of meters while standing still;
 *  - samples closer than [minDistanceM] are noise, not progress;
 *  - points implying implausible speed are GPS jumps, not running.
 */
class TrackFilter(
    private val maxAccuracyM: Float = DEFAULT_MAX_ACCURACY_M,
    private val maxSpeedMps: Double = DEFAULT_MAX_SPEED_MPS,
    private val minDistanceM: Double = DEFAULT_MIN_DISTANCE_M,
) {
    /** An accepted sample plus the distance it added from the previous accepted sample. */
    data class Accepted(val point: TrackPoint, val addedDistanceM: Double)

    private var anchor: TrackPoint? = null

    /** Called when a run starts or resumes so the gap across a pause is never measured. */
    fun reset() {
        anchor = null
    }

    fun accept(point: TrackPoint): Accepted? {
        if (!point.accuracyM.isFinite() || point.accuracyM > maxAccuracyM) return null

        val last = anchor
        if (last == null) {
            anchor = point
            return Accepted(point, 0.0)
        }

        val dtSeconds = (point.recordedAt - last.recordedAt) / 1000.0
        if (dtSeconds <= 0.0) return null

        val distance = Geo.distanceMeters(last.lat, last.lon, point.lat, point.lon)
        if (distance < minDistanceM) return null
        if (distance / dtSeconds > maxSpeedMps) return null

        anchor = point
        return Accepted(point, distance)
    }

    companion object {
        const val DEFAULT_MAX_ACCURACY_M = 35f
        const val DEFAULT_MAX_SPEED_MPS = 12.0 // ~43 km/h
        const val DEFAULT_MIN_DISTANCE_M = 1.0
    }
}
