package com.ishireader.app.data.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ishireader.app.data.local.AnnotationOutboxDao
import com.ishireader.app.data.local.CachedAniListEntryDao
import com.ishireader.app.data.local.CachedLibraryPrefsDao
import com.ishireader.app.data.local.DailyListeningBucketDao
import com.ishireader.app.data.local.DailyReadingBucketDao
import com.ishireader.app.data.local.ListeningTimeCacheDao
import com.ishireader.app.data.local.PageCountCacheDao
import com.ishireader.app.data.local.PendingAniListPatchDao
import com.ishireader.app.data.local.PendingLibraryPrefsPatchDao
import com.ishireader.app.data.local.PositionDao
import com.ishireader.app.data.local.ReadingSpeedSampleDao
import com.ishireader.app.data.local.ReadingTimeCacheDao
import com.ishireader.app.data.local.WordCountCacheDao
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.repository.LibraryMetadataPrefetcher

/**
 * Workers that need real dependencies (a DAO, the network client) can't rely on WorkManager's
 * default no-arg reflection -- this hand-rolled factory plugs them in instead of pulling in a DI
 * framework for the one class that needs it. Registered via IshiReaderApp's
 * Configuration.Provider, which also requires disabling WorkManager's default auto-initializer
 * in AndroidManifest.xml.
 */
class IshiWorkerFactory(
    private val positionDao: PositionDao,
    private val cachedLibraryPrefsDao: CachedLibraryPrefsDao,
    private val pendingLibraryPrefsPatchDao: PendingLibraryPrefsPatchDao,
    private val readingTimeDao: ReadingTimeCacheDao,
    private val dailyReadingBucketDao: DailyReadingBucketDao,
    private val readingSpeedSampleDao: ReadingSpeedSampleDao,
    private val wordCountCacheDao: WordCountCacheDao,
    private val pageCountCacheDao: PageCountCacheDao,
    private val annotationOutboxDao: AnnotationOutboxDao,
    private val listeningTimeDao: ListeningTimeCacheDao,
    private val dailyListeningBucketDao: DailyListeningBucketDao,
    private val libraryMetadataPrefetcher: LibraryMetadataPrefetcher,
    private val pendingAniListPatchDao: PendingAniListPatchDao,
    private val cachedAniListEntryDao: CachedAniListEntryDao,
    private val network: NetworkModule
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        PositionSyncWorker::class.java.name ->
            PositionSyncWorker(appContext, workerParameters, positionDao, network)
        LibraryPrefsSyncWorker::class.java.name ->
            LibraryPrefsSyncWorker(appContext, workerParameters, cachedLibraryPrefsDao, pendingLibraryPrefsPatchDao, network)
        ReadingTimerSyncWorker::class.java.name ->
            ReadingTimerSyncWorker(
                appContext, workerParameters,
                readingTimeDao, dailyReadingBucketDao, readingSpeedSampleDao, wordCountCacheDao, pageCountCacheDao,
                network
            )
        AnnotationsSyncWorker::class.java.name ->
            AnnotationsSyncWorker(appContext, workerParameters, annotationOutboxDao, network)
        ListeningTimerSyncWorker::class.java.name ->
            ListeningTimerSyncWorker(appContext, workerParameters, listeningTimeDao, dailyListeningBucketDao, network)
        LibraryMetadataSyncWorker::class.java.name ->
            LibraryMetadataSyncWorker(appContext, workerParameters, libraryMetadataPrefetcher, network)
        AniListSyncWorker::class.java.name ->
            AniListSyncWorker(appContext, workerParameters, pendingAniListPatchDao, cachedAniListEntryDao, network)
        else -> null
    }
}
