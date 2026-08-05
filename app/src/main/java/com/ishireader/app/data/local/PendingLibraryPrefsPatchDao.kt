package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PendingLibraryPrefsPatchDao {

    @Query("SELECT * FROM pending_library_prefs_patch WHERE id = 0")
    suspend fun get(): PendingLibraryPrefsPatchEntity?

    @Upsert
    suspend fun set(entity: PendingLibraryPrefsPatchEntity)

    @Query("DELETE FROM pending_library_prefs_patch WHERE id = 0")
    suspend fun clear()
}
