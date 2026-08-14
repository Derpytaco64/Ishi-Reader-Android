package com.ishireader.app.data.repository

import com.ishireader.app.data.local.ExactPageCountDao
import com.ishireader.app.data.local.ExactPageCountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists PageCountSweeper's full-book results keyed by manifestUrl + layout fingerprint (see
 *  ReaderSettings.layoutFingerprint), so a book reopened under settings/device dimensions it's
 *  already been swept for gets its exact page count instantly instead of re-sweeping. */
class ExactPageCountRepository(private val dao: ExactPageCountDao) {

    suspend fun get(cacheKey: String): Map<String, Int>? = withContext(Dispatchers.IO) {
        dao.get(cacheKey)?.let { entity ->
            runCatching { Json.decodeFromString<Map<String, Int>>(entity.resourcePageCountsJson) }.getOrNull()
        }
    }

    suspend fun put(cacheKey: String, resourcePageCounts: Map<String, Int>) = withContext(Dispatchers.IO) {
        dao.upsert(
            ExactPageCountEntity(
                cacheKey = cacheKey,
                resourcePageCountsJson = Json.encodeToString(resourcePageCounts),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    /** Drops every cached sweep for [manifestUrl] regardless of which layout fingerprint it was
     *  swept under -- used by the reader's "Recalculate page numbers" action, since a bad sweep
     *  (e.g. a resource that hit PageCountSweeper's timeout and fell back to 1 page) has no other
     *  way to get cleared short of app data. Deletes by manifestUrl prefix rather than a single
     *  cacheKey since the current fingerprint isn't necessarily the only -- or the broken -- one
     *  cached for this book. */
    suspend fun deleteForBook(manifestUrl: String) = withContext(Dispatchers.IO) {
        dao.deleteAllForManifest("$manifestUrl::")
    }
}
