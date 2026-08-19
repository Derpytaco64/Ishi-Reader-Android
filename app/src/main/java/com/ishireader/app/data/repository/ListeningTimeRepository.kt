package com.ishireader.app.data.repository

import com.ishireader.app.data.local.AnnotationCacheDao
import com.ishireader.app.data.local.AnnotationOutboxDao
import com.ishireader.app.data.local.DailyListeningBucketCacheEntity
import com.ishireader.app.data.local.DailyListeningBucketDao
import com.ishireader.app.data.local.ListeningTimeCacheDao
import com.ishireader.app.data.local.ListeningTimeCacheEntity
import com.ishireader.app.data.local.PendingDailyListeningBucketDeltaEntity
import com.ishireader.app.data.model.CompletedListenUpsertRequest
import com.ishireader.app.data.model.DailyListeningBucket
import com.ishireader.app.data.model.ListeningTimeData
import com.ishireader.app.data.model.StoredCompletedListen
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.sync.AnnotationKind
import com.ishireader.app.data.sync.ListeningTimerReconciler
import com.ishireader.app.data.sync.LocalFirstAnnotationStore
import com.ishireader.app.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local-first, ported from [ReadingTimerRepository]'s own fix for the same bug class -- see
 * [ListeningTimerReconciler]'s doc comment for the mechanism. The old version of this class was
 * network-first with zero local buffering and flushed listeningTime/dailyListeningHistory as
 * whole-value overwrites, the audiobook counterpart of the incident that wiped reading-time data.
 * completedListens didn't need that treatment -- each write only touches its own id, so it reuses
 * [LocalFirstAnnotationStore] (see [AnnotationKind.COMPLETED_LISTEN]) instead of duplicating the
 * outbox pattern.
 */
class ListeningTimeRepository(
    private val network: NetworkModule,
    private val listeningTimeDao: ListeningTimeCacheDao,
    private val bucketDao: DailyListeningBucketDao,
    annotationCacheDao: AnnotationCacheDao,
    annotationOutboxDao: AnnotationOutboxDao,
    private val syncScheduler: SyncScheduler
) {
    private val reconciler = ListeningTimerReconciler(listeningTimeDao, bucketDao, network)
    private val bucketListJson = ListSerializer(DailyListeningBucket.serializer())

    private val completedListens = LocalFirstAnnotationStore(
        kind = AnnotationKind.COMPLETED_LISTEN,
        serializer = StoredCompletedListen.serializer(),
        idOf = { it.id },
        cacheDao = annotationCacheDao,
        outboxDao = annotationOutboxDao,
        syncScheduler = syncScheduler,
        fetchFromServer = { manifestUrl ->
            try {
                val response = network.api.getCompletedListens(manifestUrl)
                val body = response.body()
                if (response.isSuccessful && body != null) ApiResult.Success(body.items)
                else ApiResult.Failure("Couldn't load completed listens (${response.code()})")
            } catch (e: Exception) {
                ApiResult.Failure(e.message ?: "Network error")
            }
        },
        pushUpsert = { manifestUrl, item -> network.api.upsertCompletedListen(CompletedListenUpsertRequest(manifestUrl, item)).isSuccessful },
        pushDelete = { manifestUrl, id -> network.api.deleteCompletedListen(manifestUrl, id).isSuccessful }
    )

    // --- Listening time (accumulated seconds + current listen-through marker) -----------------

    suspend fun getListeningTime(manifestUrl: String): ApiResult<ListeningTimeData?> = withContext(Dispatchers.IO) {
        val merged = reconciler.reconcileListeningTime(manifestUrl)
        if (merged != null) return@withContext ApiResult.Success(merged)
        val cache = listeningTimeDao.get(manifestUrl)
        if (cache != null) ApiResult.Success(ListeningTimeData(cache.cachedSeconds, cache.startedAt))
        else ApiResult.Failure("Couldn't load listening time -- offline, nothing cached yet")
    }

    /** Additive flush from the in-player tracker -- never overwrites the server outright, only
     *  folds forward this device's not-yet-confirmed delta (see
     *  [ListeningTimerReconciler.reconcileListeningTime]). [startedAt] isn't merge-computed, just
     *  this device's latest known marker. */
    suspend fun syncListeningTime(manifestUrl: String, accumulatedSeconds: Double, startedAt: Double?): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val cache = listeningTimeDao.get(manifestUrl)
        val baseline = if (cache?.forceOverwrite == true) cache.lastSyncedBaselineSeconds else (cache?.lastSyncedBaselineSeconds ?: 0.0)
        listeningTimeDao.upsert(
            ListeningTimeCacheEntity(manifestUrl, accumulatedSeconds, baseline, startedAt, pendingSync = true, forceOverwrite = false, updatedAtMillis = now())
        )
        syncScheduler.scheduleListeningTimerSync()
        if (reconciler.reconcileListeningTime(manifestUrl) != null) ApiResult.Success(Unit) else ApiResult.Failure("Offline -- queued for sync", isNetworkError = true)
    }

    // --- Completed listens (upsert/delete by id, no merge-math needed) -------------------------

    suspend fun getCompletedListens(manifestUrl: String): ApiResult<List<StoredCompletedListen>> =
        withContext(Dispatchers.IO) { completedListens.getAll(manifestUrl) }

    suspend fun saveCompletedListen(manifestUrl: String, item: StoredCompletedListen): ApiResult<Unit> = withContext(Dispatchers.IO) {
        completedListens.save(manifestUrl, item)
        ApiResult.Success(Unit)
    }

    suspend fun deleteCompletedListen(manifestUrl: String, id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        completedListens.delete(manifestUrl, id)
        ApiResult.Success(Unit)
    }

    // --- Daily listening history (per book, current listen-through only) -----------------------

    suspend fun getDailyListeningHistory(manifestUrl: String): ApiResult<List<DailyListeningBucket>> = withContext(Dispatchers.IO) {
        val merged = reconciler.reconcileDailyBuckets(manifestUrl)
        if (merged != null) return@withContext ApiResult.Success(merged)
        val cached = bucketDao.getCached(manifestUrl)?.bucketsJson?.let(::decodeBuckets)
        if (cached != null) ApiResult.Success(cached) else ApiResult.Failure("Couldn't load daily listening history -- offline, nothing cached yet")
    }

    /** Additive -- [deltaBuckets] are increments (seconds/progressionDelta to add per date) since
     *  the last successful sync, not a full snapshot. */
    suspend fun addDailyListeningHistoryDelta(manifestUrl: String, deltaBuckets: List<DailyListeningBucket>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (deltaBuckets.isEmpty()) return@withContext ApiResult.Success(Unit)
        val existing = bucketDao.getPendingDelta(manifestUrl)
        val existingDeltas = if (existing?.forceOverwrite == true) emptyList() else existing?.deltaBucketsJson?.let(::decodeBuckets) ?: emptyList()
        val combined = mergeDeltaLists(existingDeltas, deltaBuckets)
        bucketDao.upsertPendingDelta(
            PendingDailyListeningBucketDeltaEntity(manifestUrl, Json.encodeToString(bucketListJson, combined), forceOverwrite = false, updatedAtMillis = now())
        )
        syncScheduler.scheduleListeningTimerSync()
        if (reconciler.reconcileDailyBuckets(manifestUrl) != null) ApiResult.Success(Unit) else ApiResult.Failure("Offline -- queued for sync", isNetworkError = true)
    }

    /** Called when a listen-through completes and archives onto a [StoredCompletedListen] --
     *  intentional absolute set to empty for the *current* listen-through's buckets, bypassing the
     *  additive merge, mirroring [ReadingTimerRepository.resetDailyReadingHistory]. */
    suspend fun resetDailyListeningHistory(manifestUrl: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        bucketDao.upsertPendingDelta(
            PendingDailyListeningBucketDeltaEntity(manifestUrl, Json.encodeToString(bucketListJson, emptyList()), forceOverwrite = true, updatedAtMillis = now())
        )
        syncScheduler.scheduleListeningTimerSync()
        if (reconciler.reconcileDailyBuckets(manifestUrl) != null) ApiResult.Success(Unit) else ApiResult.Failure("Offline -- queued for sync", isNetworkError = true)
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun decodeBuckets(json: String): List<DailyListeningBucket> =
        runCatching { Json.decodeFromString(bucketListJson, json) }.getOrDefault(emptyList())

    private fun mergeDeltaLists(a: List<DailyListeningBucket>, b: List<DailyListeningBucket>): List<DailyListeningBucket> {
        val merged = linkedMapOf<String, DailyListeningBucket>()
        (a + b).forEach { delta ->
            val existing = merged[delta.date]
            merged[delta.date] = if (existing == null) delta else existing.copy(
                seconds = existing.seconds + delta.seconds,
                progressionDelta = existing.progressionDelta + delta.progressionDelta
            )
        }
        return merged.values.toList()
    }

    private fun now() = System.currentTimeMillis()
}
