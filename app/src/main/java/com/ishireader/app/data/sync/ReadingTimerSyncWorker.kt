package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.ishireader.app.data.local.DailyReadingBucketDao
import com.ishireader.app.data.local.PageCountCacheDao
import com.ishireader.app.data.local.ReadingSpeedSampleDao
import com.ishireader.app.data.local.ReadingTimeCacheDao
import com.ishireader.app.data.local.WordCountCacheDao
import com.ishireader.app.data.network.NetworkModule

/**
 * Drains every pending reading-timer outbox row (seconds, per-book daily-history deltas, the
 * global WPM sample buffer, unposted word counts) via [ReadingTimerReconciler]'s GET-merge-POST,
 * exactly like [PositionSyncWorker] does for position. Runs whenever [SyncScheduler.
 * scheduleReadingTimerSync] enqueues it -- typically right after an inline flush attempt inside
 * ReadingTimerRepository failed because the device was offline.
 */
class ReadingTimerSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val readingTimeDao: ReadingTimeCacheDao,
    private val bucketDao: DailyReadingBucketDao,
    private val speedSampleDao: ReadingSpeedSampleDao,
    private val wordCountDao: WordCountCacheDao,
    private val pageCountDao: PageCountCacheDao,
    private val network: NetworkModule
) : CoroutineWorker(context, params) {

    private val reconciler = ReadingTimerReconciler(readingTimeDao, bucketDao, speedSampleDao, wordCountDao, pageCountDao, network)

    override suspend fun doWork(): Result {
        if (!network.isConfigured) return Result.retry()

        var anyFailed = false

        val pendingSeconds = readingTimeDao.getPending()
        for (entity in pendingSeconds) {
            if (reconciler.reconcileSeconds(entity.manifestUrl) == null) anyFailed = true
        }

        val pendingBuckets = bucketDao.getAllPendingDeltas()
        for (entity in pendingBuckets) {
            if (reconciler.reconcileDailyBuckets(entity.manifestUrl) == null) anyFailed = true
        }

        if (speedSampleDao.getPending() != null) {
            if (reconciler.reconcileSpeedSamples() == null) anyFailed = true
        }

        val unpostedWordCounts = wordCountDao.getUnposted()
        for (entity in unpostedWordCounts) {
            reconciler.reconcileWordCount(entity.manifestUrl)
            if (wordCountDao.get(entity.manifestUrl)?.posted != true) anyFailed = true
        }

        return if (anyFailed) Result.retry() else Result.success()
    }
}
