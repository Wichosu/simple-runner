package com.wichosu.simplerunner.data

import com.wichosu.simplerunner.data.local.RunEntity
import com.wichosu.simplerunner.data.local.TrackPointEntity

/**
 * The persistence surface [com.wichosu.simplerunner.tracking.RunSession] needs.
 *
 * An interface rather than the repository concrete class so the session's state
 * transitions can be unit-tested in memory, without Room or Robolectric.
 */
interface RunStore {
    suspend fun activeRun(): RunEntity?
    suspend fun save(run: RunEntity)
    suspend fun addPoint(point: TrackPointEntity)
    suspend fun maxSeq(runId: String): Int
    suspend fun deleteRun(id: String)
}
