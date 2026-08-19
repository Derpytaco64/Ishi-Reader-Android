package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row cache of the last-known merged global WPM sample buffer (see
 *  ReadingSpeedSamplesRequest's doc comment -- one buffer per user, not per book), same single-row
 *  convention as [CachedLibraryPrefsEntity]. [samplesJson] is a JSON-encoded
 *  `List<ReadingSpeedSample>`. */
@Entity(tableName = "reading_speed_sample_cache")
data class ReadingSpeedSampleCacheEntity(
    @PrimaryKey val id: Int = 0,
    val samplesJson: String,
    val updatedAtMillis: Long
)
