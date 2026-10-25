package com.wichosu.simplerunner.data

import com.wichosu.simplerunner.data.local.RunDao
import com.wichosu.simplerunner.data.local.RunEntity
import com.wichosu.simplerunner.data.local.TrackPointDao
import com.wichosu.simplerunner.data.local.TrackPointEntity
import kotlinx.coroutines.flow.Flow

class RunRepository(
    private val runDao: RunDao,
    private val trackPointDao: TrackPointDao,
) : RunStore {
    val runs: Flow<List<RunEntity>> = runDao.observeRuns()

    fun run(id: String): Flow<RunEntity?> = runDao.observeRun(id)

    fun points(runId: String): Flow<List<TrackPointEntity>> = trackPointDao.observePoints(runId)

    override suspend fun activeRun(): RunEntity? = runDao.activeRun()

    override suspend fun save(run: RunEntity) = runDao.upsert(run)

    override suspend fun addPoint(point: TrackPointEntity) = trackPointDao.insert(point)

    override suspend fun maxSeq(runId: String): Int = trackPointDao.maxSeq(runId)

    override suspend fun deleteRun(id: String) = runDao.delete(id)
}
