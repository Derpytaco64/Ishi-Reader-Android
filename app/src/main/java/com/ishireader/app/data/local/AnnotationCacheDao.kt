package com.ishireader.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AnnotationCacheDao {

    @Query("SELECT * FROM annotation_cache WHERE kind = :kind AND manifestUrl = :manifestUrl")
    suspend fun get(kind: String, manifestUrl: String): AnnotationCacheEntity?

    @Upsert
    suspend fun set(entity: AnnotationCacheEntity)
}
