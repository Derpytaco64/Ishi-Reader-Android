package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ExactPageCountDao {

    @Query("SELECT * FROM exact_page_counts WHERE cacheKey = :cacheKey")
    suspend fun get(cacheKey: String): ExactPageCountEntity?

    @Upsert
    suspend fun upsert(entity: ExactPageCountEntity)
}
