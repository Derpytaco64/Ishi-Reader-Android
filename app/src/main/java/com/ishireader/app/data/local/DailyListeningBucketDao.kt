package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DailyListeningBucketDao {

    @Query("SELECT * FROM daily_listening_bucket_cache WHERE manifestUrl = :manifestUrl")
    suspend fun getCached(manifestUrl: String): DailyListeningBucketCacheEntity?

    @Upsert
    suspend fun setCached(entity: DailyListeningBucketCacheEntity)

    @Query("SELECT * FROM pending_daily_listening_bucket_delta WHERE manifestUrl = :manifestUrl")
    suspend fun getPendingDelta(manifestUrl: String): PendingDailyListeningBucketDeltaEntity?

    @Query("SELECT * FROM pending_daily_listening_bucket_delta")
    suspend fun getAllPendingDeltas(): List<PendingDailyListeningBucketDeltaEntity>

    @Upsert
    suspend fun upsertPendingDelta(entity: PendingDailyListeningBucketDeltaEntity)

    @Query("DELETE FROM pending_daily_listening_bucket_delta WHERE manifestUrl = :manifestUrl")
    suspend fun clearPendingDelta(manifestUrl: String)
}
