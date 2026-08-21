package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ComicReadingProgressionDao {

    @Query("SELECT * FROM comic_reading_progression_cache WHERE manifestUrl = :manifestUrl")
    suspend fun get(manifestUrl: String): ComicReadingProgressionCacheEntity?

    @Upsert
    suspend fun upsert(entity: ComicReadingProgressionCacheEntity)
}
