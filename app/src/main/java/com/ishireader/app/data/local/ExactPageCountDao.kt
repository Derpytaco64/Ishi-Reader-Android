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

    /** Drops every cached sweep for one book (all layout fingerprints, not just the current one --
     *  see ExactPageCountRepository.deleteForBook), so the next [resolveExactPageCounts] call is
     *  forced to re-sweep instead of replaying whatever's cached, e.g. after a resource fell back
     *  to a wrong 1-page count from a sweep timeout. */
    @Query("DELETE FROM exact_page_counts WHERE cacheKey LIKE :manifestUrlPrefix || '%'")
    suspend fun deleteAllForManifest(manifestUrlPrefix: String)
}
