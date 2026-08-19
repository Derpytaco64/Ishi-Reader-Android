package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DailyReadingBucketDao {

    @Query("SELECT * FROM daily_reading_bucket_cache WHERE manifestUrl = :manifestUrl")
    suspend fun getCached(manifestUrl: String): DailyReadingBucketCacheEntity?

    @Upsert
    suspend fun setCached(entity: DailyReadingBucketCacheEntity)

    @Query("SELECT * FROM pending_daily_reading_bucket_delta WHERE manifestUrl = :manifestUrl")
    suspend fun getPendingDelta(manifestUrl: String): PendingDailyReadingBucketDeltaEntity?

    @Query("SELECT * FROM pending_daily_reading_bucket_delta")
    suspend fun getAllPendingDeltas(): List<PendingDailyReadingBucketDeltaEntity>

    @Upsert
    suspend fun upsertPendingDelta(entity: PendingDailyReadingBucketDeltaEntity)

    @Query("DELETE FROM pending_daily_reading_bucket_delta WHERE manifestUrl = :manifestUrl")
    suspend fun clearPendingDelta(manifestUrl: String)
}
