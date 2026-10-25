package com.wichosu.simplerunner.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One recorded GPS fix.
 *
 * [segment] increments on every resume, so a paused gap must be drawn as separate
 * polylines instead of a straight line across wherever the user went during the pause.
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val segment: Int,
    val seq: Int,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val speedMps: Float?,
    val elevationM: Double?,
    val recordedAt: Long,
)
