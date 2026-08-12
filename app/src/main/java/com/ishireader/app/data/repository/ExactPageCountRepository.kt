package com.ishireader.app.data.repository

import com.ishireader.app.data.local.ExactPageCountDao
import com.ishireader.app.data.local.ExactPageCountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A full-book page-count sweep's already-recomputed layout (see
 *  DynamicPageCountTracker.recompute) -- [resourceStartPages]/[resourcePageCounts] are both
 *  reading-order href -> value, matching DynamicPageCountState's own fields exactly, so a caller
 *  can hand these straight to [com.ishireader.app.reader.dynamicPageForLocator] without needing
 *  the book's Publication/reading order at all. */
data class ExactPageLayout(
    val resourceStartPages: Map<String, Int>,
    val resourcePageCounts: Map<String, Int>
) {
    val totalPages: Int get() = resourcePageCounts.values.sum()
}

/** Persists PageCountSweeper's full-book results keyed by manifestUrl + layout fingerprint (see
 *  ReaderSettings.layoutFingerprint), so a book reopened under settings/device dimensions it's
 *  already been swept for gets its exact page count instantly instead of re-sweeping. */
class ExactPageCountRepository(private val dao: ExactPageCountDao) {

    suspend fun get(manifestUrl: String, fingerprint: String): ExactPageLayout? =
        withContext(Dispatchers.IO) {
            dao.get(cacheKey(manifestUrl, fingerprint))?.toLayoutOrNull()
        }

    suspend fun put(manifestUrl: String, fingerprint: String, layout: ExactPageLayout) =
        withContext(Dispatchers.IO) {
            dao.upsert(
                ExactPageCountEntity(
                    cacheKey = cacheKey(manifestUrl, fingerprint),
                    manifestUrl = manifestUrl,
                    resourceStartPagesJson = Json.encodeToString(layout.resourceStartPages),
                    resourcePageCountsJson = Json.encodeToString(layout.resourcePageCounts),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }

    /** Best-effort real page count for [manifestUrl] regardless of the current settings/device
     *  fingerprint -- for callers (the book detail screen's progress dial) that want *a* real page
     *  number for this book rather than none, and have no live reader/layout to measure against
     *  right now. Whatever was most recently swept, possibly under different settings than are
     *  active today -- still far closer to the real page count than the coarse
     *  totalProgression-based estimate it'd otherwise fall back to. */
    suspend fun getLatestForManifest(manifestUrl: String): ExactPageLayout? =
        withContext(Dispatchers.IO) {
            dao.getLatestForManifest(manifestUrl)?.toLayoutOrNull()
        }

    private fun cacheKey(manifestUrl: String, fingerprint: String) = "$manifestUrl::$fingerprint"

    private fun ExactPageCountEntity.toLayoutOrNull(): ExactPageLayout? = runCatching {
        ExactPageLayout(
            resourceStartPages = Json.decodeFromString<Map<String, Int>>(resourceStartPagesJson),
            resourcePageCounts = Json.decodeFromString<Map<String, Int>>(resourcePageCountsJson)
        )
    }.getOrNull()
}
