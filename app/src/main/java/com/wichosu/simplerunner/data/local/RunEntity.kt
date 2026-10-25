package com.wichosu.simplerunner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved run.
 *
 * `endedAt == null` marks a run that is still open, including one whose process died
 * mid-run. `lastResumeAt` is the wall clock at the start of the current un-paused span,
 * which is what makes moving time recoverable after a crash.
 */
@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val movingMillis: Long,
    val lastResumeAt: Long?,
    val distanceM: Double,
    val maxSpeedMps: Double,
    val segmentCount: Int,
    val createdAt: Long,
)
