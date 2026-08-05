package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CachedUserStatsDao {

    @Query("SELECT * FROM cached_user_stats WHERE id = 0")
    suspend fun get(): CachedUserStatsEntity?

    @Upsert
    suspend fun set(entity: CachedUserStatsEntity)
}
