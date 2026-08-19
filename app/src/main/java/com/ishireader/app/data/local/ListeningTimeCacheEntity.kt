package com.ishireader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * On-device durable state for one audiobook's accumulated-seconds counter -- audiobook counterpart
 * of [ReadingTimeCacheEntity], same merge fields and same reasoning (see that class's doc comment).
 * [startedAt] is carried alongside the counter because the server's listeningTime route POSTs both
 * together (see ListeningTimeRequest); it's a state marker rather than an accumulator, so it's just
 * this device's latest known value, not merge-computed like [cachedSeconds].
 */
@Entity(tableName = "listening_time_cache")
data class ListeningTimeCacheEntity(
    @PrimaryKey val manifestUrl: String,
    val cachedSeconds: Double,
    val lastSyncedBaselineSeconds: Double,
    val startedAt: Double?,
    val pendingSync: Boolean,
    val forceOverwrite: Boolean,
    val updatedAtMillis: Long
)
