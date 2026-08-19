package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Outbox for one book's daily-history writes made since the last successful sync --
 * [deltaBucketsJson] is a JSON-encoded `List<DailyReadingBucket>` of *increments* (seconds/words/
 * progressionDelta to add per date), not a snapshot. [com.ishireader.app.data.sync.
 * ReadingTimerReconciler] folds this onto a fresh server read (summing matching dates, creating
 * new ones) before POSTing the merged full list back -- the server route only accepts a whole-list
 * overwrite (see DailyReadingHistoryRequest's doc comment), so reconciling client-side before every
 * push is what stops an offline session's empty/partial view from clobbering the real history.
 * Cleared once a flush succeeds; [forceOverwrite] mirrors [ReadingTimeCacheEntity]'s -- the user's
 * own "reset timer" action means "the server's history should become empty," not "merge nothing
 * in," so it bypasses the fold-in-and-merge step.
 */
@Entity(tableName = "pending_daily_reading_bucket_delta")
data class PendingDailyReadingBucketDeltaEntity(
    @PrimaryKey val manifestUrl: String,
    val deltaBucketsJson: String,
    val forceOverwrite: Boolean,
    val updatedAtMillis: Long
)
