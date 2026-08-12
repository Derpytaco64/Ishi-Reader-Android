package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A full-book page-count sweep (see PageCountSweeper), persisted so it never has to be redone for
 * a book opened again under settings/device dimensions it's already been measured for. [cacheKey]
 * is manifestUrl + the settings/viewport layout fingerprint (see ReaderSettings.layoutFingerprint)
 * joined together -- one row per distinct book+layout combination ever swept on this device.
 * [manifestUrl] duplicates the same book identity [cacheKey] already encodes, kept as its own
 * indexed column (rather than parsed back out of cacheKey) so callers who don't have -- or don't
 * care about -- the exact current layout fingerprint (e.g. the book detail screen's progress
 * dial, which just wants *some* real page count for this book, not one for its own never-measured
 * layout) can look up the most recently swept row for a book regardless of which fingerprint
 * produced it (see ExactPageCountDao.getLatestForManifest).
 *
 * [resourceStartPagesJson]/[resourcePageCountsJson] are each a `Map<String, Int>` (reading-order
 * href -> value) serialized via kotlinx.serialization, mirroring PositionEntity.locatorJson's
 * reasoning for storing serialized blobs rather than a join table: these shapes are only ever
 * read/written whole, never queried by their inner keys. Stored as
 * [DynamicPageCountTracker][com.ishireader.app.reader.DynamicPageCountTracker] already computes
 * them (post-recompute), not as PageCountSweeper's raw per-resource counts alone, specifically so
 * a reader elsewhere in the app (see above) can reconstruct a page number from a saved Locator
 * without needing the book's Publication/reading order at all -- resourceStartPages already
 * carries each resource's absolute starting page, a plain href lookup away.
 */
@Entity(tableName = "exact_page_counts", indices = [Index("manifestUrl")])
data class ExactPageCountEntity(
    @PrimaryKey val cacheKey: String,
    val manifestUrl: String,
    val resourceStartPagesJson: String,
    val resourcePageCountsJson: String,
    val updatedAtMillis: Long
)
