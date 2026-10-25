package com.wichosu.simplerunner.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    /**
     * Must be a real upsert, not `@Insert(onConflict = REPLACE)`.
     *
     * REPLACE deletes the existing row and inserts a new one, and the cascade on
     * [TrackPointEntity.runId] then deletes every point of the run. The run row is
     * rewritten every second, so that would erase the whole track as it is recorded.
     */
    @Upsert
    suspend fun upsert(run: RunEntity)

    @Query("SELECT * FROM runs WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeRun(): RunEntity?

    @Query("SELECT * FROM runs ORDER BY startedAt DESC")
    fun observeRuns(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :id")
    fun observeRun(id: String): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun getRun(id: String): RunEntity?

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: String)
}
