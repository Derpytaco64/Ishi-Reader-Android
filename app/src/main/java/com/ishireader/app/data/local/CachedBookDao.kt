package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedBookDao {

    @Query("SELECT * FROM cached_books")
    suspend fun getAll(): List<CachedBookEntity>

    @Query("DELETE FROM cached_books")
    suspend fun clear()

    @Insert
    suspend fun insertAll(entities: List<CachedBookEntity>)

    /** Full replace rather than an upsert-by-url -- a book removed from the server's library
     *  (deleted/renamed on disk) must also disappear from the cache, not linger forever. */
    @Transaction
    suspend fun replaceAll(entities: List<CachedBookEntity>) {
        clear()
        insertAll(entities)
    }
}
