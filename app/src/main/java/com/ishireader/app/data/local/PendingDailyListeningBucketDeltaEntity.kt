package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Audiobook counterpart of [PendingDailyReadingBucketDeltaEntity] -- outbox of increments
 *  (seconds/progressionDelta to add per date) since the last successful sync, folded onto a fresh
 *  server read by [com.ishireader.app.data.sync.ListeningTimerReconciler] before POSTing the merged
 *  full list back. [forceOverwrite] is set when a listen-through completes (the current listen's
 *  daily buckets should become empty, not merge nothing in -- see ListeningTimeTracker.completeListen). */
@Entity(tableName = "pending_daily_listening_bucket_delta")
data class PendingDailyListeningBucketDeltaEntity(
    @PrimaryKey val manifestUrl: String,
    val deltaBucketsJson: String,
    val forceOverwrite: Boolean,
    val updatedAtMillis: Long
)
