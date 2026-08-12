package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The on-device source of truth for reading position -- the app reads/writes this table, never
 * the network, directly. [locatorJson] is the Locator's own toJSON() output verbatim (Readium's
 * model classes own that shape; duplicating it into typed columns would just drift). [progression]
 * is pulled out of locatorJson.locations.totalProgression at write time so callers can read a
 * percent-complete figure without re-parsing the whole locator -- it's informational only
 * (PositionReconciler resolves sync conflicts by [pendingSync] -- a pending local write always
 * wins -- not by progress, so paging backwards to re-read a chapter isn't clobbered by an older,
 * further-along save).
 *
 * [exactPercent] is the same page-accurate percent ReaderActivity's own footer displays at save
 * time (see positionDisplayText/pageFraction), persisted verbatim so the book detail screen's
 * progress dial can show that exact figure instead of recomputing one from whatever page-count
 * sweep happens to be cached -- which can be stale relative to the settings/device this save was
 * made under (see ExactPageCountRepository.getLatestForManifest) and so disagree with the reader.
 * Null for audiobooks (which don't save through this path with a page fraction), pre-existing
 * rows saved before this field existed, and positions adopted from the server (which doesn't
 * carry this figure) -- callers fall back to the coarser recompute in that case.
 */
@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val manifestUrl: String,
    val locatorJson: String,
    val progression: Double?,
    val updatedAtMillis: Long,
    val pendingSync: Boolean,
    val exactPercent: Double? = null
)
