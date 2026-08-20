package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PendingAniListPatchDao {

    @Query("SELECT * FROM pending_anilist_patch")
    suspend fun getAll(): List<PendingAniListPatchEntity>

    @Query("SELECT * FROM pending_anilist_patch WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Int): PendingAniListPatchEntity?

    @Upsert
    suspend fun upsert(entity: PendingAniListPatchEntity)

    @Query("DELETE FROM pending_anilist_patch WHERE mediaId = :mediaId")
    suspend fun remove(mediaId: Int)
}
