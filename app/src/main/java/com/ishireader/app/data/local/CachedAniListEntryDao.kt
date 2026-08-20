package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CachedAniListEntryDao {

    @Query("SELECT * FROM cached_anilist_entry WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Int): CachedAniListEntryEntity?

    @Upsert
    suspend fun set(entity: CachedAniListEntryEntity)
}
