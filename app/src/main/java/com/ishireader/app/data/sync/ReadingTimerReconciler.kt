package com.ishireader.app.data.sync

import com.ishireader.app.data.local.DailyReadingBucketCacheEntity
import com.ishireader.app.data.local.DailyReadingBucketDao
import com.ishireader.app.data.local.PageCountCacheDao
import com.ishireader.app.data.local.PageCountCacheEntity
import com.ishireader.app.data.local.PendingDailyReadingBucketDeltaEntity
import com.ishireader.app.data.local.PendingReadingSpeedSamplesEntity
import com.ishireader.app.data.local.ReadingSpeedSampleCacheEntity
import com.ishireader.app.data.local.ReadingSpeedSampleDao
import com.ishireader.app.data.local.ReadingTimeCacheDao
import com.ishireader.app.data.local.ReadingTimeCacheEntity
import com.ishireader.app.data.local.WordCountCacheDao
import com.ishireader.app.data.local.WordCountCacheEntity
import com.ishireader.app.data.model.DailyReadingBucket
import com.ishireader.app.data.model.DailyReadingHistoryRequest
import com.ishireader.app.data.model.ReadingSpeedSample
import com.ishireader.app.data.model.ReadingSpeedSamplesRequest
import com.ishireader.app.data.model.ReadingTimeRequest
import com.ishireader.app.data.model.WordCountRequest
import com.ishireader.app.data.network.NetworkModule
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_SPEED_SAMPLES = 50

/**
 * Everything that made the offline-mode data loss possible lived in one design flaw: the old
 * ReadingTimerRepository treated readingTime/dailyReadingHistory/readingSpeedSamples as
 * network-first with no local cache, and flushed each one as a *whole-value overwrite* (matching
 * the server routes, which really do just `writeJsonFileAtomic` the raw POST body -- see
 * readingTime/route.ts). Opening a book while the server was down seeded the in-reader tracker's
 * counters from a failed GET (0 seconds, empty buckets, empty -- and for speed samples, *global*
 * per-user, not per-book -- sample buffer). If a flush ever reached the server afterwards (any
 * background pause once connectivity returned, even mid-session), that zeroed/partial local state
 * overwrote the real totals outright.
 *
 * This reconciler is the fix: every sync is GET-the-current-server-value, fold in only what this
 * device knows it hasn't confirmed yet (never blindly resend what it started with), then POST the
 * merged result. A GET failure (still offline) leaves everything queued rather than attempting a
 * write from an unconfirmed baseline. Mirrors PositionReconciler's shape -- shared by an inline
 * best-effort call from the repository and by [ReadingTimerSyncWorker] draining the outbox in the
 * background.
 */
class ReadingTimerReconciler(
    private val readingTimeDao: ReadingTimeCacheDao,
    private val bucketDao: DailyReadingBucketDao,
    private val speedSampleDao: ReadingSpeedSampleDao,
    private val wordCountDao: WordCountCacheDao,
    private val pageCountDao: PageCountCacheDao,
    private val network: NetworkModule
) {
    private val bucketListJson = ListSerializer(DailyReadingBucket.serializer())
    private val sampleListJson = ListSerializer(ReadingSpeedSample.serializer())

    /** Returns the authoritative total after folding any locally-pending delta onto a fresh server
     *  read, or null if the server is unreachable -- callers fall back to the local cache then. */
    suspend fun reconcileSeconds(manifestUrl: String): Double? {
        val serverSeconds = fetchSeconds(manifestUrl) ?: return null
        val cache = readingTimeDao.get(manifestUrl)
        if (cache == null || !cache.pendingSync) {
            readingTimeDao.upsert(
                ReadingTimeCacheEntity(manifestUrl, serverSeconds, serverSeconds, pendingSync = false, forceOverwrite = false, updatedAtMillis = now())
            )
            return serverSeconds
        }

        val merged = if (cache.forceOverwrite) {
            cache.cachedSeconds
        } else {
            serverSeconds + (cache.cachedSeconds - cache.lastSyncedBaselineSeconds).coerceAtLeast(0.0)
        }

        if (!postSeconds(manifestUrl, merged)) return merged

        // Re-read rather than trusting the stale `cache` snapshot: the tracker may have ticked
        // cachedSeconds further while this POST was in flight (see PositionReconciler's identical
        // re-read-after-POST comment for why blindly clearing pendingSync here would silently
        // drop that newer, still-unsynced delta).
        val current = readingTimeDao.get(manifestUrl)
        if (current != null && current.cachedSeconds == cache.cachedSeconds) {
            readingTimeDao.upsert(current.copy(lastSyncedBaselineSeconds = merged, pendingSync = false, forceOverwrite = false))
        }
        return merged
    }

    suspend fun reconcileDailyBuckets(manifestUrl: String): List<DailyReadingBucket>? {
        val serverBuckets = fetchBuckets(manifestUrl) ?: return null
        val pending = bucketDao.getPendingDelta(manifestUrl)
        if (pending == null) {
            cacheBuckets(manifestUrl, serverBuckets)
            return serverBuckets
        }

        val deltas = decodeBuckets(pending.deltaBucketsJson)
        val merged = if (pending.forceOverwrite) deltas else mergeBuckets(serverBuckets, deltas)

        if (!postBuckets(manifestUrl, merged)) {
            cacheBuckets(manifestUrl, merged)
            return merged
        }

        val current = bucketDao.getPendingDelta(manifestUrl)
        if (current != null && current.deltaBucketsJson == pending.deltaBucketsJson) {
            bucketDao.clearPendingDelta(manifestUrl)
        }
        cacheBuckets(manifestUrl, merged)
        return merged
    }

    suspend fun reconcileSpeedSamples(): List<ReadingSpeedSample>? {
        val serverSamples = fetchSpeedSamples() ?: return null
        val pending = speedSampleDao.getPending()
        if (pending == null) {
            cacheSpeedSamples(serverSamples)
            return serverSamples
        }

        val newSamples = decodeSamples(pending.samplesJson)
        val merged = (serverSamples + newSamples).takeLast(MAX_SPEED_SAMPLES)

        if (!postSpeedSamples(merged)) {
            cacheSpeedSamples(merged)
            return merged
        }

        val current = speedSampleDao.getPending()
        if (current != null && current.samplesJson == pending.samplesJson) {
            speedSampleDao.clearPending()
        }
        cacheSpeedSamples(merged)
        return merged
    }

    /** Posts an unposted local word count once (see WordCountCacheEntity's doc comment) -- a no-op
     *  if this device has nothing pending or the server already has a value. */
    suspend fun reconcileWordCount(manifestUrl: String): Double? {
        val cache = wordCountDao.get(manifestUrl)
        val response = try { network.api.getWordCount(manifestUrl) } catch (e: Exception) { null }
        val serverValue = response?.takeIf { it.isSuccessful }?.body()?.wordCount
        if (serverValue != null) {
            if (cache == null || !cache.posted) {
                wordCountDao.upsert(WordCountCacheEntity(manifestUrl, serverValue, posted = true))
            }
            return serverValue
        }
        if (cache != null && !cache.posted) {
            val postOk = try {
                network.api.setWordCount(WordCountRequest(manifestUrl, cache.wordCount)).isSuccessful
            } catch (e: Exception) { false }
            if (postOk) wordCountDao.upsert(cache.copy(posted = true))
        }
        return cache?.wordCount
    }

    suspend fun refreshPageCount(manifestUrl: String): Int? {
        val response = try { network.api.getPageCount(manifestUrl) } catch (e: Exception) { null }
        val pageCount = response?.takeIf { it.isSuccessful }?.body()?.pageCount ?: return null
        pageCountDao.upsert(PageCountCacheEntity(manifestUrl, pageCount))
        return pageCount
    }

    // --- helpers ---------------------------------------------------------------------------

    private suspend fun fetchSeconds(manifestUrl: String): Double? =
        try {
            val response = network.api.getReadingTime(manifestUrl)
            if (response.isSuccessful) response.body()?.seconds ?: 0.0 else null
        } catch (e: Exception) { null }

    private suspend fun postSeconds(manifestUrl: String, seconds: Double): Boolean =
        try { network.api.setReadingTime(ReadingTimeRequest(manifestUrl, seconds)).isSuccessful } catch (e: Exception) { false }

    private suspend fun fetchBuckets(manifestUrl: String): List<DailyReadingBucket>? =
        try {
            val response = network.api.getDailyReadingHistory(manifestUrl)
            if (response.isSuccessful) response.body()?.buckets ?: emptyList() else null
        } catch (e: Exception) { null }

    private suspend fun postBuckets(manifestUrl: String, buckets: List<DailyReadingBucket>): Boolean =
        try { network.api.setDailyReadingHistory(DailyReadingHistoryRequest(manifestUrl, buckets)).isSuccessful } catch (e: Exception) { false }

    private suspend fun fetchSpeedSamples(): List<ReadingSpeedSample>? =
        try {
            val response = network.api.getReadingSpeedSamples()
            if (response.isSuccessful) response.body()?.samples ?: emptyList() else null
        } catch (e: Exception) { null }

    private suspend fun postSpeedSamples(samples: List<ReadingSpeedSample>): Boolean =
        try { network.api.setReadingSpeedSamples(ReadingSpeedSamplesRequest(samples)).isSuccessful } catch (e: Exception) { false }

    private suspend fun cacheBuckets(manifestUrl: String, buckets: List<DailyReadingBucket>) {
        bucketDao.setCached(DailyReadingBucketCacheEntity(manifestUrl, Json.encodeToString(bucketListJson, buckets), now()))
    }

    private suspend fun cacheSpeedSamples(samples: List<ReadingSpeedSample>) {
        speedSampleDao.setCached(ReadingSpeedSampleCacheEntity(samplesJson = Json.encodeToString(sampleListJson, samples), updatedAtMillis = now()))
    }

    private fun decodeBuckets(json: String): List<DailyReadingBucket> =
        runCatching { Json.decodeFromString(bucketListJson, json) }.getOrDefault(emptyList())

    private fun decodeSamples(json: String): List<ReadingSpeedSample> =
        runCatching { Json.decodeFromString(sampleListJson, json) }.getOrDefault(emptyList())

    private fun mergeBuckets(server: List<DailyReadingBucket>, deltas: List<DailyReadingBucket>): List<DailyReadingBucket> {
        val merged = linkedMapOf<String, DailyReadingBucket>()
        server.forEach { merged[it.date] = it }
        deltas.forEach { delta ->
            val existing = merged[delta.date] ?: DailyReadingBucket(date = delta.date)
            merged[delta.date] = existing.copy(
                seconds = existing.seconds + delta.seconds,
                words = existing.words + delta.words,
                progressionDelta = existing.progressionDelta + delta.progressionDelta
            )
        }
        return merged.values.toList()
    }

    private fun now() = System.currentTimeMillis()
}
