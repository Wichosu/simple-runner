package com.wichosu.simplerunner.domain

/** A single accepted-or-candidate GPS sample, independent of Android's Location type. */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val recordedAt: Long,
    val speedMps: Float? = null,
    val elevationM: Double? = null,
)
