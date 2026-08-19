package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Audiobook counterpart of [DailyReadingBucketCacheEntity] -- last-known merged view of a book's
 *  daily listening history, what a fully offline getDailyListeningHistory falls back to. */
@Entity(tableName = "daily_listening_bucket_cache")
data class DailyListeningBucketCacheEntity(
    @PrimaryKey val manifestUrl: String,
    val bucketsJson: String,
    val updatedAtMillis: Long
)
