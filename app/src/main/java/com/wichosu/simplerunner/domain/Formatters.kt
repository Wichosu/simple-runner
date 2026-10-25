package com.wichosu.simplerunner.domain

import java.util.Locale

/** Display formatting and derived pace values. Pure Kotlin, JVM-testable. */
object Formatters {

    /** Below this the GPS has not settled; below this distance a pace is meaningless. */
    const val MIN_DISTANCE_FOR_PACE_M = 100.0

    private const val MIN_PACE_SEC_PER_KM = 120.0 // 2:00 /km
    private const val MAX_PACE_SEC_PER_KM = 1800.0 // 30:00 /km

    /** 65_000 -> "01:05", 3_725_000 -> "1:02:05". */
    fun duration(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** Seconds per kilometer -> "5:32". Returns "--:--" when absent or implausible. */
    fun pace(secondsPerKm: Double?): String {
        if (secondsPerKm == null || secondsPerKm.isNaN()) return "--:--"
        if (secondsPerKm < MIN_PACE_SEC_PER_KM || secondsPerKm > MAX_PACE_SEC_PER_KM) return "--:--"

        val whole = secondsPerKm.toLong()
        return String.format(Locale.US, "%d:%02d", whole / 60, whole % 60)
    }

    /** Meters -> "1.23". */
    fun distanceKm(meters: Double): String = String.format(Locale.US, "%.2f", meters / 1000.0)

    /** Null until enough distance exists for the number to mean anything. */
    fun paceSecondsPerKm(movingMillis: Long, distanceM: Double): Double? {
        if (distanceM < MIN_DISTANCE_FOR_PACE_M) return null
        return (movingMillis / 1000.0) / (distanceM / 1000.0)
    }
}
