package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Last-known merged view of a book's daily reading history (server buckets with any locally
 *  pending delta already folded in for display) -- what a fully offline `getDailyReadingHistory`
 *  falls back to instead of an empty list. [bucketsJson] is a JSON-encoded
 *  `List<DailyReadingBucket>`, kept as a raw string the same way [CachedLibraryPrefsEntity] and
 *  [CachedBookEntity] cache their payloads, since Room doesn't need to query into it. */
@Entity(tableName = "daily_reading_bucket_cache")
data class DailyReadingBucketCacheEntity(
    @PrimaryKey val manifestUrl: String,
    val bucketsJson: String,
    val updatedAtMillis: Long
)
