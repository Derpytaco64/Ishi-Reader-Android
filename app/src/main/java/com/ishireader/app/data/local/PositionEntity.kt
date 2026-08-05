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
 */
@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val manifestUrl: String,
    val locatorJson: String,
    val progression: Double?,
    val updatedAtMillis: Long,
    val pendingSync: Boolean
)
