package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CachedUserDao {

    @Query("SELECT * FROM cached_user WHERE id = 0")
    suspend fun get(): CachedUserEntity?

    @Upsert
    suspend fun set(entity: CachedUserEntity)
}
