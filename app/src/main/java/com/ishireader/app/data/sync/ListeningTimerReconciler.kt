package com.ishireader.app.data.sync

import com.ishireader.app.data.local.DailyListeningBucketCacheEntity
import com.ishireader.app.data.local.DailyListeningBucketDao
import com.ishireader.app.data.local.ListeningTimeCacheDao
import com.ishireader.app.data.local.ListeningTimeCacheEntity
import com.ishireader.app.data.local.PendingDailyListeningBucketDeltaEntity
import com.ishireader.app.data.model.DailyListeningBucket
import com.ishireader.app.data.model.DailyListeningHistoryRequest
import com.ishireader.app.data.model.ListeningTimeData
import com.ishireader.app.data.model.ListeningTimeRequest
import com.ishireader.app.data.network.NetworkModule
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Audiobook counterpart of [ReadingTimerReconciler] -- same GET-merge-POST fix for the same class
 * of bug: `listeningTime`/`dailyListeningHistory` (src/app/api/userdata/{listeningTime,
 * dailyListeningHistory}/route.ts) are whole-value `writeJsonFileAtomic` overwrites, and the old
 * ListeningTimeRepository was network-first with no local cache, so an offline listen session's
 * zeroed/partial seed could clobber real totals the moment a background flush reached the server --
 * identical mechanism to the reading-timer incident this was ported from. See that class's doc
 * comment for the full mechanism.
 */
class ListeningTimerReconciler(
    private val listeningTimeDao: ListeningTimeCacheDao,
    private val bucketDao: DailyListeningBucketDao,
    private val network: NetworkModule
) {
    private val bucketListJson = ListSerializer(DailyListeningBucket.serializer())

    /** Returns the authoritative (seconds, startedAt) after folding any locally-pending delta onto
     *  a fresh server read, or null if the server is unreachable. [startedAt] isn't merge-computed
     *  like [ListeningTimeCacheEntity.cachedSeconds] -- it's a state marker only this device's
     *  tracker sets, so a pending write's local value simply wins. */
    suspend fun reconcileListeningTime(manifestUrl: String): ListeningTimeData? {
        val server = fetchListeningTime(manifestUrl) ?: return null
        val cache = listeningTimeDao.get(manifestUrl)
        if (cache == null || !cache.pendingSync) {
            listeningTimeDao.upsert(
                ListeningTimeCacheEntity(
                    manifestUrl, server.accumulatedSeconds, server.accumulatedSeconds, server.startedAt,
                    pendingSync = false, forceOverwrite = false, updatedAtMillis = now()
                )
            )
            return server
        }

        val mergedSeconds = if (cache.forceOverwrite) {
            cache.cachedSeconds
        } else {
            server.accumulatedSeconds + (cache.cachedSeconds - cache.lastSyncedBaselineSeconds).coerceAtLeast(0.0)
        }
        val merged = ListeningTimeData(mergedSeconds, cache.startedAt)

        if (!postListeningTime(manifestUrl, merged)) return merged

        val current = listeningTimeDao.get(manifestUrl)
        if (current != null && current.cachedSeconds == cache.cachedSeconds && current.startedAt == cache.startedAt) {
            listeningTimeDao.upsert(current.copy(lastSyncedBaselineSeconds = mergedSeconds, pendingSync = false, forceOverwrite = false))
        }
        return merged
    }

    suspend fun reconcileDailyBuckets(manifestUrl: String): List<DailyListeningBucket>? {
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

    // --- helpers ---------------------------------------------------------------------------

    private suspend fun fetchListeningTime(manifestUrl: String): ListeningTimeData? =
        try {
            val response = network.api.getListeningTime(manifestUrl)
            if (response.isSuccessful) response.body()?.data ?: ListeningTimeData() else null
        } catch (e: Exception) { null }

    private suspend fun postListeningTime(manifestUrl: String, data: ListeningTimeData): Boolean =
        try { network.api.setListeningTime(ListeningTimeRequest(manifestUrl, data.accumulatedSeconds, data.startedAt)).isSuccessful } catch (e: Exception) { false }

    private suspend fun fetchBuckets(manifestUrl: String): List<DailyListeningBucket>? =
        try {
            val response = network.api.getDailyListeningHistory(manifestUrl)
            if (response.isSuccessful) response.body()?.buckets ?: emptyList() else null
        } catch (e: Exception) { null }

    private suspend fun postBuckets(manifestUrl: String, buckets: List<DailyListeningBucket>): Boolean =
        try { network.api.setDailyListeningHistory(DailyListeningHistoryRequest(manifestUrl, buckets)).isSuccessful } catch (e: Exception) { false }

    private suspend fun cacheBuckets(manifestUrl: String, buckets: List<DailyListeningBucket>) {
        bucketDao.setCached(DailyListeningBucketCacheEntity(manifestUrl, Json.encodeToString(bucketListJson, buckets), now()))
    }

    private fun decodeBuckets(json: String): List<DailyListeningBucket> =
        runCatching { Json.decodeFromString(bucketListJson, json) }.getOrDefault(emptyList())

    private fun mergeBuckets(server: List<DailyListeningBucket>, deltas: List<DailyListeningBucket>): List<DailyListeningBucket> {
        val merged = linkedMapOf<String, DailyListeningBucket>()
        server.forEach { merged[it.date] = it }
        deltas.forEach { delta ->
            val existing = merged[delta.date] ?: DailyListeningBucket(date = delta.date)
            merged[delta.date] = existing.copy(
                seconds = existing.seconds + delta.seconds,
                progressionDelta = existing.progressionDelta + delta.progressionDelta
            )
        }
        return merged.values.toList()
    }

    private fun now() = System.currentTimeMillis()
}
