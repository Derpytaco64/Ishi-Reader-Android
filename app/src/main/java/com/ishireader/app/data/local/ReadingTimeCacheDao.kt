package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReadingTimeCacheDao {

    @Query("SELECT * FROM reading_time_cache WHERE manifestUrl = :manifestUrl")
    suspend fun get(manifestUrl: String): ReadingTimeCacheEntity?

    @Query("SELECT * FROM reading_time_cache WHERE pendingSync = 1")
    suspend fun getPending(): List<ReadingTimeCacheEntity>

    @Upsert
    suspend fun upsert(entity: ReadingTimeCacheEntity)
}
