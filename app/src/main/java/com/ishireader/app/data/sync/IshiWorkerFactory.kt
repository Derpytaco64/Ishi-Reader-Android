package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ishireader.app.data.local.PositionDao
import com.ishireader.app.data.network.NetworkModule

/**
 * Workers that need real dependencies (a DAO, the network client) can't rely on WorkManager's
 * default no-arg reflection -- this hand-rolled factory plugs them in instead of pulling in a DI
 * framework for the one class that needs it. Registered via IshiReaderApp's
 * Configuration.Provider, which also requires disabling WorkManager's default auto-initializer
 * in AndroidManifest.xml.
 */
class IshiWorkerFactory(
    private val positionDao: PositionDao,
    private val network: NetworkModule
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        PositionSyncWorker::class.java.name ->
            PositionSyncWorker(appContext, workerParameters, positionDao, network)
        else -> null
    }
}
