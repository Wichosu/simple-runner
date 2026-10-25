package com.wichosu.simplerunner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {

    @Insert
    suspend fun insert(point: TrackPointEntity)

    @Insert
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE runId = :runId ORDER BY seq ASC")
    fun observePoints(runId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE runId = :runId ORDER BY seq ASC")
    suspend fun getPoints(runId: String): List<TrackPointEntity>

    @Query("SELECT COALESCE(MAX(seq), 0) FROM track_points WHERE runId = :runId")
    suspend fun maxSeq(runId: String): Int

    @Query("DELETE FROM track_points WHERE runId = :runId")
    suspend fun deleteForRun(runId: String)
}
