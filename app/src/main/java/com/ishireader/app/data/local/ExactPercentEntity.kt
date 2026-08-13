package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The page-accurate reading percent ReaderActivity's own footer shows at save time (see
 * positionDisplayText/pageFraction), cached purely so the book detail screen's progress dial and
 * every cover's progress border can show that same exact figure instead of the coarser
 * `totalProgression`-based one PositionEntity.progression gives, which drifts from the real page
 * count since chapters vary in text density.
 *
 * Deliberately its own table, not a column on [PositionEntity]: the position-saving-and-sync
 * system (setPosition/PositionReconciler/PositionSyncWorker) stays exactly as it was, unaware this
 * table exists -- this figure never leaves the device and never factors into sync/conflict
 * resolution, it's read-back-verbatim display data only (see PositionRepository.localPercent).
 * Missing for audiobooks (which don't save through the reader's page-fraction path), a book never
 * opened in the reader on this device, or a position adopted from the server -- callers fall back
 * to PositionEntity.progression in those cases.
 */
@Entity(tableName = "exact_percents")
data class ExactPercentEntity(
    @PrimaryKey val manifestUrl: String,
    val percent: Double,
    val updatedAtMillis: Long
)
