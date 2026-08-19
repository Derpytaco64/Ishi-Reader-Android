package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WordCountCacheDao {

    @Query("SELECT * FROM word_count_cache WHERE manifestUrl = :manifestUrl")
    suspend fun get(manifestUrl: String): WordCountCacheEntity?

    @Query("SELECT * FROM word_count_cache WHERE posted = 0")
    suspend fun getUnposted(): List<WordCountCacheEntity>

    @Upsert
    suspend fun upsert(entity: WordCountCacheEntity)
}
