package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CachedLibraryPrefsDao {

    @Query("SELECT * FROM cached_library_prefs WHERE id = 0")
    suspend fun get(): CachedLibraryPrefsEntity?

    @Upsert
    suspend fun set(entity: CachedLibraryPrefsEntity)
}
