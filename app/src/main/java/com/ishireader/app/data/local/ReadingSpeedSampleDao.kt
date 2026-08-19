package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReadingSpeedSampleDao {

    @Query("SELECT * FROM reading_speed_sample_cache WHERE id = 0")
    suspend fun getCached(): ReadingSpeedSampleCacheEntity?

    @Upsert
    suspend fun setCached(entity: ReadingSpeedSampleCacheEntity)

    @Query("SELECT * FROM pending_reading_speed_samples WHERE id = 0")
    suspend fun getPending(): PendingReadingSpeedSamplesEntity?

    @Upsert
    suspend fun setPending(entity: PendingReadingSpeedSamplesEntity)

    @Query("DELETE FROM pending_reading_speed_samples WHERE id = 0")
    suspend fun clearPending()
}
