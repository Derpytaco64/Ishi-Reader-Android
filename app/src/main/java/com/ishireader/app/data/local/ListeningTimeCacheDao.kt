package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ListeningTimeCacheDao {

    @Query("SELECT * FROM listening_time_cache WHERE manifestUrl = :manifestUrl")
    suspend fun get(manifestUrl: String): ListeningTimeCacheEntity?

    @Query("SELECT * FROM listening_time_cache WHERE pendingSync = 1")
    suspend fun getPending(): List<ListeningTimeCacheEntity>

    @Upsert
    suspend fun upsert(entity: ListeningTimeCacheEntity)
}
