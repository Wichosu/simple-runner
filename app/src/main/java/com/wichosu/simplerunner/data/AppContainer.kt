package com.wichosu.simplerunner.data

import android.content.Context
import com.wichosu.simplerunner.data.local.SimpleRunnerDatabase
import com.wichosu.simplerunner.tracking.RunSession
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher

class AppContainer(context: Context) {
    private val database = SimpleRunnerDatabase.get(context)

    val runRepository = RunRepository(
        runDao = database.runDao(),
        trackPointDao = database.trackPointDao(),
    )

    /** App-lifetime scope: the run must outlive any screen or service instance. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * One thread for all run writes. Launch order is therefore execution order, which
     * keeps the run row ahead of the points that reference it.
     */
    private val persistenceDispatcher = Executors
        .newSingleThreadExecutor { runnable ->
            Thread(runnable, "run-persistence").apply { isDaemon = true }
        }
        .asCoroutineDispatcher()

    val runSession = RunSession(
        store = runRepository,
        scope = appScope,
        persistenceDispatcher = persistenceDispatcher,
    )
}
