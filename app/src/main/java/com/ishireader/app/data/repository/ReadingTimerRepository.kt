package com.ishireader.app.data.repository

import com.ishireader.app.data.local.DailyReadingBucketDao
import com.ishireader.app.data.local.PageCountCacheDao
import com.ishireader.app.data.local.PendingDailyReadingBucketDeltaEntity
import com.ishireader.app.data.local.PendingReadingSpeedSamplesEntity
import com.ishireader.app.data.local.ReadingSpeedSampleDao
import com.ishireader.app.data.local.ReadingTimeCacheDao
import com.ishireader.app.data.local.ReadingTimeCacheEntity
import com.ishireader.app.data.local.WordCountCacheDao
import com.ishireader.app.data.local.WordCountCacheEntity
import com.ishireader.app.data.model.DailyReadingBucket
import com.ishireader.app.data.model.ReadingSpeedSample
import com.ishireader.app.data.model.WordCountRequest
import com.ishireader.app.data.network.ApiResult
import com.ishireader.app.data.network.NetworkModule
import com.ishireader.app.data.sync.ReadingTimerReconciler
import com.ishireader.app.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_SPEED_SAMPLES = 50

/**
 * Local-first, same spirit as PositionRepository/LibraryPrefsRepository: every write lands in Room
 * durably *before* any network attempt, and reads fall back to that cache when the server is
 * unreachable instead of silently presenting 0/empty. The old version of this class was
 * network-first with zero local buffering and flushed each field as a whole-value overwrite --
 * that's what let a reading session started offline (seeded from a failed GET) clobber the real
 * server totals the moment a background flush finally reached the server. See
 * [ReadingTimerReconciler]'s doc comment for the full mechanism and the merge-not-overwrite fix.
 */
class ReadingTimerRepository(
    private val network: NetworkModule,
    private val readingTimeDao: ReadingTimeCacheDao,
    private val bucketDao: DailyReadingBucketDao,
    private val speedSampleDao: ReadingSpeedSampleDao,
    private val wordCountDao: WordCountCacheDao,
    private val pageCountDao: PageCountCacheDao,
    private val syncScheduler: SyncScheduler
) {
    private val reconciler = ReadingTimerReconciler(readingTimeDao, bucketDao, speedSampleDao, wordCountDao, pageCountDao, network)
    private val bucketListJson = ListSerializer(DailyReadingBucket.serializer())
    private val sampleListJson = ListSerializer(ReadingSpeedSample.serializer())

    // --- Reading time (seconds) -------------------------------------------------------------

    suspend fun getReadingTimeSeconds(manifestUrl: String): ApiResult<Double?> = withContext(Dispatchers.IO) {
        val merged = reconciler.reconcileSeconds(manifestUrl)
        if (merged != null) return@withContext ApiResult.Success(merged)
        val cached = readingTimeDao.get(manifestUrl)?.cachedSeconds
        if (cached != null) ApiResult.Success(cached) else ApiResult.Failure("Couldn't load reading time -- offline, nothing cached yet")
    }

    /** Additive flush from the in-reader tracker's running total -- never overwrites the server
     *  outright, only folds forward whatever this device hasn't confirmed synced yet (see
     *  [ReadingTimerReconciler.reconcileSeconds]). Durable immediately; queued for background
     *  retry via [SyncScheduler.scheduleReadingTimerSync] if the inline attempt can't reach the
     *  server right now. */
    suspend fun syncReadingTimeSeconds(manifestUrl: String, seconds: Double): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val cache = readingTimeDao.get(manifestUrl)
        val baseline = if (cache?.forceOverwrite == true) cache.lastSyncedBaselineSeconds else (cache?.lastSyncedBaselineSeconds ?: 0.0)
        readingTimeDao.upsert(
            ReadingTimeCacheEntity(manifestUrl, cachedSeconds = seconds, lastSyncedBaselineSeconds = baseline, pendingSync = true, forceOverwrite = false, updatedAtMillis = now())
        )
        syncScheduler.scheduleReadingTimerSync()
        if (reconciler.reconcileSeconds(manifestUrl) != null) ApiResult.Success(Unit) else ApiResult.Failure("Offline -- queued for sync", isNetworkError = true)
    }

    /** The user's own "reset timer" action -- an intentional absolute set, not a background flush,
     *  so it bypasses the additive merge and forces the server to 0 once connectivity allows. */
    suspend fun resetReadingTimeSeconds(manifestUrl: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        readingTimeDao.upsert(
            ReadingTimeCacheEntity(manifestUrl, cachedSeconds = 0.0, lastSyncedBaselineSeconds = 0.0, pendingSync = true, forceOverwrite = true, updatedAtMillis = now())
        )
        syncScheduler.scheduleReadingTimerSync()
        if (reconciler.reconcileSeconds(manifestUrl) != null) ApiResult.Success(Unit) else ApiResult.Failure("Offline -- queued for sync", isNetworkError = true)
    }

    // --- Word count ----------------------------------------------------------------------------

    suspend fun getWordCount(manifestUrl: String): ApiResult<Double?> = withContext(Dispatchers.IO) {
        val value = reconciler.reconcileWordCount(manifestUrl)
        if (value != null) ApiResult.Success(value) else ApiResult.Failure("Couldn't load word count (${manifestUrl})")
    }

    /** Persisted once, forever -- callers should only call this the first time a book's word count
     *  is computed locally. Cached immediately so an offline computation isn't silently redone
     *  every session; queued for a one-time POST via [ReadingTimerSyncWorker] if offline now. */
    suspend fun setWordCount(manifestUrl: String, wordCount: Double): ApiResult<Unit> = withContext(Dispatchers.IO) {
        wordCountDao.upsert(WordCountCacheEntity(manifestUrl, wordCount, posted = false))
        val postOk = try {
            network.api.setWordCount(WordCountRequest(manifestUrl, wordCount)).isSuccessful
        } catch (e: Exception) { false }
        if (postOk) {
            wordCountDao.upsert(WordCountCacheEntity(manifestUrl, wordCount, posted = true))
            ApiResult.Success(Unit)
        } else {
            syncScheduler.scheduleReadingTimerSync()
            ApiResult.Success(Unit)
        }
    }

    // --- Page count (GET-only, server-computed -- see PageCountResponse's doc comment) ---------

    suspend fun getPageCount(manifestUrl: String): ApiResult<Int?> = withContext(Dispatchers.IO) {
        val fresh = reconciler.refreshPageCount(manifestUrl)
        if (fresh != null) return@withContext ApiResult.Success(fresh)
        val cached = pageCountDao.get(manifestUrl)?.pageCount
        if (cached != null) ApiResult.Success(cached) else ApiResult.Failure("Couldn't load page count -- offline, nothing cached yet")
    }

    // --- Reading speed samples (global per-user buffer) -----------------------------------------

    suspend fun getReadingSpeedSamples(): ApiResult<List<ReadingSpeedSample>> = withContext(Dispatchers.IO) {
        val merged = reconciler.reconcileSpeedSamples()
        if (merged != null) return@withContext ApiResult.Success(merged)
        val cached = speedSampleDao.getCached()?.samplesJson?.let(::decodeSamples)
        if (cached != null) ApiResult.Success(cached) else ApiResult.Failure("Couldn't load reading speed samples -- offline, nothing cached yet")
    }

    /** Additive -- [newSamples] are only the samples recorded since the last successful sync, not
     *  the tracker's whole in-memory buffer (that used to be what got POSTed wholesale, which is
     *  how an offline session's empty seed wiped every other book's WPM history -- see
     *  [ReadingTimerReconciler]'s doc comment). */
    suspend fun addReadingSpeedSamples(newSamples: List<ReadingSpeedSample>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (newSamples.isEmpty()) return@withContext ApiResult.Success(Unit)
        val existingPending = speedSampleDao.getPending()?.samplesJson?.let(::decodeSamples) ?: emptyList()
        val combined = (existingPending + newSamples).takeLast(MAX_SPEED_SAMPLES)
        speedSampleDao.setPending(PendingReadingSpeedSamplesEntity(samplesJson = Json.encodeToString(sampleListJson, combined)))
        syncScheduler.scheduleReadingTimerSync()
        if (reconciler.reconcileSpeedSamples() != null) ApiResult.Success(Unit) else ApiResult.Failure("Offline -- queued for sync", isNetworkError = true)
    }

    // --- Daily reading history (per book) --------------------------------------------------------

    suspend fun getDailyReadingHistory(manifestUrl: String): ApiResult<List<DailyReadingBucket>> = withContext(Dispatchers.IO) {
        val merged = reconciler.reconcileDailyBuckets(manifestUrl)
        if (merged != null) return@withContext ApiResult.Success(merged)
        val cached = bucketDao.getCached(manifestUrl)?.bucketsJson?.let(::decodeBuckets)
        if (cached != null) ApiResult.Success(cached) else ApiResult.Failure("Couldn't load daily reading history -- offline, nothing cached yet")
    }

    /** Additive -- [deltaBuckets] are increments (seconds/words/progressionDelta to add per date)
     *  since the last successful sync, not a full snapshot. Folded onto whatever pending delta is
     *  already queued (in case an earlier offline flush hasn't synced yet either) before attempting
     *  to reconcile. */
    suspend fun addDailyReadingHistoryDelta(manifestUrl: String, deltaBuckets: List<DailyReadingBucket>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (deltaBuckets.isEmpty()) return@withContext ApiResult.Success(Unit)
        val existing = bucketDao.getPendingDelta(manifestUrl)
        val existingDeltas = if (existing?.forceOverwrite == true) emptyList() else existing?.deltaBucketsJson?.let(::decodeBuckets) ?: emptyList()
        val combined = mergeDeltaLists(existingDeltas, deltaBuckets)
        bucketDao.upsertPendingDelta(
            PendingDailyReadingBucketDeltaEntity(manifestUrl, Json.encodeToString(bucketListJson, combined), forceOverwrite = false, updatedAtMillis = now())
        )
        syncScheduler.scheduleReadingTimerSync()
        if (reconciler.reconcileDailyBuckets(manifestUrl) != null) ApiResult.Success(Unit) else ApiResult.Failure("Offline -- queued for sync", isNetworkError = true)
    }

    /** The user's own "reset timer" action (save-or-discard clears the daily history too) --
     *  intentional absolute set to empty, bypassing the additive merge. */
    suspend fun resetDailyReadingHistory(manifestUrl: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        bucketDao.upsertPendingDelta(
            PendingDailyReadingBucketDeltaEntity(manifestUrl, Json.encodeToString(bucketListJson, emptyList()), forceOverwrite = true, updatedAtMillis = now())
        )
        syncScheduler.scheduleReadingTimerSync()
        if (reconciler.reconcileDailyBuckets(manifestUrl) != null) ApiResult.Success(Unit) else ApiResult.Failure("Offline -- queued for sync", isNetworkError = true)
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun decodeBuckets(json: String): List<DailyReadingBucket> =
        runCatching { Json.decodeFromString(bucketListJson, json) }.getOrDefault(emptyList())

    private fun decodeSamples(json: String): List<ReadingSpeedSample> =
        runCatching { Json.decodeFromString(sampleListJson, json) }.getOrDefault(emptyList())

    private fun mergeDeltaLists(a: List<DailyReadingBucket>, b: List<DailyReadingBucket>): List<DailyReadingBucket> {
        val merged = linkedMapOf<String, DailyReadingBucket>()
        (a + b).forEach { delta ->
            val existing = merged[delta.date]
            merged[delta.date] = if (existing == null) delta else existing.copy(
                seconds = existing.seconds + delta.seconds,
                words = existing.words + delta.words,
                progressionDelta = existing.progressionDelta + delta.progressionDelta
            )
        }
        return merged.values.toList()
    }

    private fun now() = System.currentTimeMillis()
}
