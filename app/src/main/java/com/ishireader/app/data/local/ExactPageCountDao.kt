package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ExactPageCountDao {

    @Query("SELECT * FROM exact_page_counts WHERE cacheKey = :cacheKey")
    suspend fun get(cacheKey: String): ExactPageCountEntity?

    /** Most recently swept row for [manifestUrl], regardless of which layout fingerprint produced
     *  it -- see ExactPageCountEntity's own doc comment for why a caller would want this instead
     *  of an exact-fingerprint [get]. */
    @Query("SELECT * FROM exact_page_counts WHERE manifestUrl = :manifestUrl ORDER BY updatedAtMillis DESC LIMIT 1")
    suspend fun getLatestForManifest(manifestUrl: String): ExactPageCountEntity?

    @Upsert
    suspend fun upsert(entity: ExactPageCountEntity)
}
