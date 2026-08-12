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
}
