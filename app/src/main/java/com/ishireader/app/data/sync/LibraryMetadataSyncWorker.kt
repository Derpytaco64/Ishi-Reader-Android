package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.repository.LibraryMetadataPrefetcher

/**
 * Runs [LibraryMetadataPrefetcher.prefetchAllDownloaded] in the background -- enqueued as a
 * one-shot right after a book finishes downloading and at app startup (see
 * [SyncScheduler.scheduleLibraryMetadataSync]), and on a standing periodic schedule (see
 * [SyncScheduler.ensureLibraryMetadataPeriodicSync]) so a book that's been downloaded but never
 * reopened still picks up reading/listening progress made elsewhere (web, another device) instead
 * of only ever reflecting whatever was true the moment it was downloaded.
 *
 * Every fetch this triggers already caches through its own repository (all local-first, see
 * ReadingTimerReconciler/ListeningTimerReconciler/LocalFirstAnnotationStore), so there's no
 * merge-math here and nothing to retry precisely -- a partial failure just means some of this
 * book's fields will catch up on the next scheduled run instead.
 */
class LibraryMetadataSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val prefetcher: LibraryMetadataPrefetcher,
    private val network: NetworkModule
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!network.isConfigured) return Result.retry()
        prefetcher.prefetchAllDownloaded()
        return Result.success()
    }
}
