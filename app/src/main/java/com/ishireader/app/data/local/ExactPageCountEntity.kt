package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A full-book page-count sweep (see PageCountSweeper), persisted so it never has to be redone for
 * a book opened again under settings/device dimensions it's already been measured for. [cacheKey]
 * is manifestUrl + the settings/viewport layout fingerprint (see ReaderSettings.layoutFingerprint)
 * joined together -- one row per distinct book+layout combination ever swept on this device.
 * [resourcePageCountsJson] is a Map<String, Int> (reading-order href -> real page count for that
 * resource) serialized via kotlinx.serialization, mirroring PositionEntity.locatorJson's reasoning
 * for storing a serialized blob rather than a join table: this shape is only ever read/written
 * whole, never queried by its inner keys.
 */
@Entity(tableName = "exact_page_counts")
data class ExactPageCountEntity(
    @PrimaryKey val cacheKey: String,
    val resourcePageCountsJson: String,
    val updatedAtMillis: Long
)
