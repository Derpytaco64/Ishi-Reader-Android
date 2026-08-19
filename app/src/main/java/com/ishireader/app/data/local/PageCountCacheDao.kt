package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PageCountCacheDao {

    @Query("SELECT * FROM page_count_cache WHERE manifestUrl = :manifestUrl")
    suspend fun get(manifestUrl: String): PageCountCacheEntity?

    @Upsert
    suspend fun upsert(entity: PageCountCacheEntity)
}
