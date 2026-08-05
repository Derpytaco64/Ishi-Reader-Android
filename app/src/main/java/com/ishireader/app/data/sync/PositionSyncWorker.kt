package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.ishireader.app.data.local.PositionDao
import com.ishireader.app.data.network.NetworkModule

/**
 * Drains the position outbox: for each locally-pending book, reconciles it against the server
 * (see [PositionReconciler] for the further-progress-wins comparison). A failed
 * comparison/push leaves the row pending and returns [Result.retry], so WorkManager reschedules
 * with backoff rather than losing the change.
 */
class PositionSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val positionDao: PositionDao,
    private val network: NetworkModule
) : CoroutineWorker(context, params) {

    private val reconciler = PositionReconciler(positionDao, network)

    override suspend fun doWork(): Result {
        // The process may have been started solely to run this worker, before any screen has
        // re-configured the server URL from saved prefs -- retry rather than crash on network.api.
        if (!network.isConfigured) return Result.retry()

        val pending = positionDao.getPending()
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (entity in pending) {
            if (!reconciler.reconcile(entity.manifestUrl)) anyFailed = true
        }
        return if (anyFailed) Result.retry() else Result.success()
    }
}
