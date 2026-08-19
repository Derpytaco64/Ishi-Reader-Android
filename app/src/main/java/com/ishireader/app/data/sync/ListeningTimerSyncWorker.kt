package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.ishireader.app.data.local.DailyListeningBucketDao
import com.ishireader.app.data.local.ListeningTimeCacheDao
import com.ishireader.app.data.network.NetworkModule

/**
 * Drains every pending listening-timer outbox row (accumulated seconds, per-book daily-history
 * deltas) via [ListeningTimerReconciler]'s GET-merge-POST -- audiobook counterpart of
 * [ReadingTimerSyncWorker]. Completed-listen writes are drained separately by
 * [AnnotationsSyncWorker] since they share its generic outbox instead.
 */
class ListeningTimerSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val listeningTimeDao: ListeningTimeCacheDao,
    private val bucketDao: DailyListeningBucketDao,
    private val network: NetworkModule
) : CoroutineWorker(context, params) {

    private val reconciler = ListeningTimerReconciler(listeningTimeDao, bucketDao, network)

    override suspend fun doWork(): Result {
        if (!network.isConfigured) return Result.retry()

        var anyFailed = false

        val pendingSeconds = listeningTimeDao.getPending()
        for (entity in pendingSeconds) {
            if (reconciler.reconcileListeningTime(entity.manifestUrl) == null) anyFailed = true
        }

        val pendingBuckets = bucketDao.getAllPendingDeltas()
        for (entity in pendingBuckets) {
            if (reconciler.reconcileDailyBuckets(entity.manifestUrl) == null) anyFailed = true
        }

        return if (anyFailed) Result.retry() else Result.success()
    }
}
